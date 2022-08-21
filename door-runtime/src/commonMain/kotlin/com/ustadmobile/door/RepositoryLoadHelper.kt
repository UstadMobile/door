package com.ustadmobile.door

import com.ustadmobile.door.lifecycle.LifecycleOwner
import com.ustadmobile.door.lifecycle.LiveData
import com.ustadmobile.door.lifecycle.MutableLiveData
import com.ustadmobile.door.lifecycle.Observer
import com.ustadmobile.door.ext.DoorTag
import io.github.aakira.napier.Napier
import com.ustadmobile.door.util.systemTimeInMillis
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import kotlin.jvm.Volatile

typealias LifeCycleHelperFactory = (LifecycleOwner) -> RepositoryLoadHelperLifecycleHelper

//Empty / null retry:
// On DataSourceFactory / boundary callback: alwasy - because it was called by onZeroItemsLoaded
// On LiveData - never - use a reference to the livedata itself, and check if it's null or empty
// On a normal or suspended return type: never. THe generated code has to check the result and call again if needed
// e.g.

class RepositoryLoadHelper<T>(
    val repository: DoorDatabaseRepository,
    val maxAttempts: Int = 3,
    val retryDelay: Int = 5000,
    val autoRetryOnEmptyLiveData: LiveData<T>? = null,
    val lifecycleHelperFactory: LifeCycleHelperFactory =
          {RepositoryLoadHelperLifecycleHelper(it)},
    val uri: String = "",
    val listMaxItemsLimit: Int = -1,
    val loadFn: suspend(endpoint: String) -> T
) : RepositoryConnectivityListener {

    class NoConnectionException(message: String, cause: Throwable? = null): Exception(message, cause)

    val completed = atomic(false)

    val requestLock = Mutex()

    @Volatile
    var loadedVal = CompletableDeferred<T>()

    //val repoHelperId = ID_ATOMICINT.getAndIncrement()

    val statusLiveData = MutableLiveData<RepoLoadStatus>(RepoLoadStatus(STATUS_NOT_STARTED))

    data class RepoLoadStatus(var loadStatus: Int = 0, var remoteNode: String? = null)

    /**
     * This wrapper class is loosely modeled on the MediatorLiveData. The difference is that there
     * is only one source.
     *
     * It also provides access to the loadingStatus LiveData
     *
     */
    inner class LiveDataWrapper2<L>(private val src: LiveData<L>): MutableLiveData<L>(), Observer<L> {

        val loadingStatus: MutableLiveData<RepoLoadStatus>
            get() = statusLiveData


        override fun onChanged(t: L) {
            setVal(t)
        }

        override fun onActive() {
            super.onActive()
            src.observeForever(this)
            onLifecycleActive()
        }

        override fun onInactive() {
            super.onInactive()
            src.removeObserver(this)
        }

    }

    @Volatile
    var status: Int = 0
        private set

    init {
        //TODO: Actually - this does not need to be weak. This can be done using the onactive and oninactive to add and remove listeners
        //repository.addWeakConnectivityListener(this)
    }

    private val logPrefix
        get() = "ID [$uri]  "

    private var wrappedLiveData: LiveData<*>? = null

    fun <L> wrapLiveData(src: LiveData<L>): LiveData<L> {
        return LiveDataWrapper2(src).also {
            wrappedLiveData = it
        }
    }


    internal fun onLifecycleActive() {
        GlobalScope.launch {
            try {
                doRequest(resetAttemptCount = true)
            }catch(e: Exception) {
                Napier.e("$logPrefix Exception running onActive callback", e)
            }
        }
    }

    @Volatile
    var triedMainEndpoint = false

    private val mirrorsTried = mutableListOf<Int>()

    @Volatile
    var attemptCount = 0

    override fun onConnectivityStatusChanged(newStatus: Int) {
        if(!completed.value && newStatus == DoorDatabaseRepository.STATUS_CONNECTED
                && wrappedLiveData?.hasActiveObservers() ?: false) {
            GlobalScope.launch {
                try {
                    Napier.d("$logPrefix RepositoryLoadHelper: onConnectivityStatusChanged: did not complete " +
                                    "and data is being observed. Trying again.")
                    doRequest(resetAttemptCount = true)
                } catch (e: Exception) {
                    Napier.e("$logPrefix RepositoryLoadHelper: onConnectivityStatusChanged: ERROR " +
                                    "did not complete and data is being observed: ", e)
                }
            }
        }
    }

    override fun onNewMirrorAvailable(mirror: MirrorEndpoint) {
        if(!completed.value && wrappedLiveData?.hasActiveObservers() ?: true) {
            GlobalScope.launch {
                try {
                    Napier.d("$logPrefix RepositoryLoadHelper: onNewMirrorAvailable: Mirror # ${mirror.mirrorId} " +
                                    "did not complete and data is being observed. Trying again.")
                    doRequest(resetAttemptCount = true)
                } catch(e: Exception) {
                    Napier.e("$logPrefix RepositoryLoadHelper: onNewMirrorAvailable: ERROR " +
                                    "did not complete and data is being observed: ", e)
                }
            }
        }
    }

    suspend fun doRequest(resetAttemptCount: Boolean = false, runAgain: Boolean = false) : T = withContext(Dispatchers.Default){
        val doRequestStart = systemTimeInMillis()
        requestLock.withLock {
            Napier.d("$logPrefix doRequest: resetAttemptCount = $resetAttemptCount")
            if(resetAttemptCount) {
                attemptCount = 0
            }

            if(runAgain) {
                loadedVal = CompletableDeferred()
                completed.value = false
            }

            val mirrorToUse: MirrorEndpoint? = null
            val endpointToUse = repository.config.endpoint
            while(!completed.value && isActive && attemptCount <= maxAttempts) {
                try {
                    attemptCount++

                    val newStatus = STATUS_LOADING_CLOUD

                    if(newStatus != status) {
                        status = newStatus
                        statusLiveData.postValue(RepoLoadStatus(status))
                    }

                    Napier.d(tag = DoorTag.LOG_TAG,
                        message = {"$logPrefix doRequest: calling loadFn using endpoint ${repository.config.endpoint} ."})
                    val t = loadFn(repository.config.endpoint)

                    val isNullOrEmpty = if(t is List<*>) {
                        t.isEmpty()
                    }else {
                        t == null
                    }

                    status = if(isNullOrEmpty) {
                        STATUS_LOADED_NODATA
                    }else {
                        STATUS_LOADED_WITHDATA
                    }

                    completed.value = true
                    loadedVal.complete(t)
                    statusLiveData.postValue(RepoLoadStatus(status))

                    Napier.d(
                        tag = DoorTag.LOG_TAG,
                        message = {
                            "$logPrefix doRequest: completed successfully from $endpointToUse in ${systemTimeInMillis() - doRequestStart}ms"
                        })
                    return@withContext t
                }catch(e: Exception) {
                    //something went wrong with the load
                    Napier.e("$logPrefix Exception attempting to load from $endpointToUse",
                            e)
                    delay(retryDelay.toLong())
                }
            }

            Napier.d("$logPrefix doRequest: over. Is completed=${loadedVal.isCompleted}")
            if(loadedVal.isCompleted) {
                return@withContext loadedVal.getCompleted()
            }else {
                val isConnected = repository.connectivityStatus == DoorDatabaseRepository.STATUS_CONNECTED
                val newStatus = if(isConnected || mirrorToUse != null) {
                    STATUS_FAILED_CONNECTION_ERR
                }else {
                    STATUS_FAILED_NOCONNECTIVITYORPEERS
                }

                if(newStatus != status) {
                    status = newStatus
                    statusLiveData.postValue(RepoLoadStatus(status))
                }


                throw Exception("$logPrefix ==ERROR== NOT completed")
            }
        }
    }

    companion object {

        private const val PREFIX_NOCONNECTION_NO_MIRRORS_MESSAGE = "LoadHelper-NOCONNECTION"

        //val ID_ATOMICINT = atomic(0)

        const val STATUS_NOT_STARTED = 0

        const val STATUS_LOADING_CLOUD = 1

        const val STATUS_LOADED_WITHDATA = 11

        const val STATUS_LOADED_NODATA = 12

        const val STATUS_FAILED_NOCONNECTIVITYORPEERS = 15

        const val STATUS_FAILED_CONNECTION_ERR = 16

    }
}