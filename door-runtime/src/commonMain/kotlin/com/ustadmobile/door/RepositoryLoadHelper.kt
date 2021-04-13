package com.ustadmobile.door

import com.github.aakira.napier.Napier
import com.ustadmobile.door.util.systemTimeInMillis
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import kotlin.jvm.Volatile

typealias LifeCycleHelperFactory = (DoorLifecycleOwner) -> RepositoryLoadHelperLifecycleHelper

//Empty / null retry:
// On DataSourceFactory / boundary callback: alwasy - because it was called by onZeroItemsLoaded
// On LiveData - never - use a reference to the livedata itself, and check if it's null or empty
// On a normal or suspended return type: never. THe generated code has to check the result and call again if needed
// e.g.
/**
 * @param autoRetryEmptyMirrorResult - if true, this assumes that an empty result from a mirror means
 * it did not have the data we were looking for. This is useful for BoundaryCallback loads, which
 * are themselves triggered by the database not having data.
 */
class RepositoryLoadHelper<T>(val repository: DoorDatabaseRepository,
                              val autoRetryEmptyMirrorResult: Boolean = false,
                              val maxAttempts: Int = 3,
                              val retryDelay: Int = 5000,
                              val autoRetryOnEmptyLiveData: DoorLiveData<T>? = null,
                              val lifecycleHelperFactory: LifeCycleHelperFactory =
                                      {RepositoryLoadHelperLifecycleHelper(it)},
                              val uri: String = "",
                              val listMaxItemsLimit: Int = -1,
                              val loadFn: suspend(endpoint: String) -> T) : RepositoryConnectivityListener {

    class NoConnectionException(message: String, cause: Throwable? = null): Exception(message, cause)

    val completed = atomic(false)

    val requestLock = Mutex()

    @Volatile
    var loadedVal = CompletableDeferred<T>()

    //val repoHelperId = ID_ATOMICINT.getAndIncrement()

    val statusLiveData = DoorMutableLiveData<RepoLoadStatus>(RepoLoadStatus(STATUS_NOT_STARTED))

    data class RepoLoadStatus(var loadStatus: Int = 0, var remoteNode: String? = null)

    /**
     * This wrapper class is loosely modeled on the MediatorLiveData. The difference is that there
     * is only one source.
     *
     * It also provides access to the loadingStatus LiveData
     *
     */
    inner class LiveDataWrapper2<L>(private val src: DoorLiveData<L>): DoorMutableLiveData<L>(), DoorObserver<L> {

        val loadingStatus: DoorMutableLiveData<RepoLoadStatus>
            get() = statusLiveData


        override fun onChanged(t: L) {
            setVal(t)
        }

        override fun onActive2() {
            super.onActive2()
            src.observeForever(this)
            onLifecycleActive()
        }

        override fun onInactive2() {
            super.onInactive2()
            src.removeObserver(this)
        }

    }

    @Volatile
    var status: Int = 0
        private set

    init {
        repository.addWeakConnectivityListener(this)
    }

    private val logPrefix
        get() = "ID [$uri]  "

    private var wrappedLiveData: DoorLiveData<*>? = null

    fun <L> wrapLiveData(src: DoorLiveData<L>): DoorLiveData<L> {
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
                triedMainEndpoint = false
            }

            if(runAgain) {
                loadedVal = CompletableDeferred()
                completed.value = false
            }

            var mirrorToUse: MirrorEndpoint? = null
            while(!completed.value && isActive && attemptCount <= maxAttempts) {
                var endpointToUse: String? = null
                try {
                    attemptCount++
                    val isConnected = repository.connectivityStatus == DoorDatabaseRepository.STATUS_CONNECTED
                    mirrorToUse = if(isConnected && !triedMainEndpoint) {
                        null as MirrorEndpoint? //use the main endpoint
                    }else {
                        repository.activeMirrors().filter { it.mirrorId !in mirrorsTried }
                                .maxBy { it.priority }
                    }

                    if(!isConnected && mirrorToUse == null) {
                        //it's hopeless - there is no mirror and we have no connection - give up
                        throw NoConnectionException("$PREFIX_NOCONNECTION_NO_MIRRORS_MESSAGE $logPrefix: " +
                                "Repository status indicates no connectivity and there are no active " +
                                "mirrors")
                    }

                    val newStatus = if(mirrorToUse != null) {
                        STATUS_LOADING_MIRROR
                    }else {
                        STATUS_LOADING_CLOUD
                    }

                    if(newStatus != status) {
                        status = newStatus
                        statusLiveData.sendValue(RepoLoadStatus(status))
                    }

                    endpointToUse = if(mirrorToUse == null) {
                        repository.config.endpoint
                    }else {
                        mirrorToUse.endpointUrl
                    }

                    Napier.d({"$logPrefix doRequest: calling loadFn using endpoint $endpointToUse ."})
                    var t = loadFn(endpointToUse)

                    if(mirrorToUse == null) {
                        triedMainEndpoint = true
                    }else {
                        mirrorsTried.add(mirrorToUse.mirrorId)
                    }

                    val isNullOrEmpty = if(t is List<*>) {
                        t.isEmpty()
                    }else {
                        t == null
                    }

                    //if it came from the main endpoint, or we got some actual data, then it looks good
                    var isMainEndpointOrNotNullOrEmpty = mirrorToUse == null || !isNullOrEmpty
                    if(isMainEndpointOrNotNullOrEmpty) {
                        //completed.value = true
                    }

                    if(!isMainEndpointOrNotNullOrEmpty && autoRetryOnEmptyLiveData != null) {
                        val liveDataVal = waitForNonEmptyLiveData()
                        if(liveDataVal != null) {
                            t = liveDataVal
                            isMainEndpointOrNotNullOrEmpty = true
                            //completed.value = true
                        }
                    }

                    if(isMainEndpointOrNotNullOrEmpty || !autoRetryEmptyMirrorResult
                            || repository.activeMirrors().filter { it.mirrorId !in mirrorsTried }.isEmpty()) {
                        status = if(isNullOrEmpty) {
                            STATUS_LOADED_NODATA
                        }else {
                            STATUS_LOADED_WITHDATA
                        }

                        completed.value = true
                        loadedVal.complete(t)
                        statusLiveData.sendValue(RepoLoadStatus(status))


                        Napier.d({"$logPrefix doRequest: completed successfully from $endpointToUse in ${systemTimeInMillis() - doRequestStart}ms"})
                        return@withContext t
                    }else {
                        Napier.e({"$logPrefix doRequest: loadFn completed from $endpointToUse but " +
                                "not successful. IsNullOrEmpty=$isNullOrEmpty, " +
                                "autoRetryOnEmptyLiveData=${autoRetryOnEmptyLiveData != null}" +
                                "autoRetryEmptyMirrorResult=$autoRetryEmptyMirrorResult"})
                    }

                    delay(retryDelay.toLong())
                }catch(e: Exception) {
                    //something went wrong with the load
                    Napier.e("$logPrefix Exception attempting to load from $endpointToUse",
                            e)
                    if(e is NoConnectionException) {
                        Napier.d({"No connection and no mirrors available - giving up"})
                        break
                    }
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
                    statusLiveData.sendValue(RepoLoadStatus(status))
                }


                throw Exception("$logPrefix ==ERROR== NOT completed")
            }
        }
    }

    fun shouldTryAnotherMirror() : Boolean {
        val isCompleted = completed.value
        return !isCompleted && attemptCount < maxAttempts
    }

    suspend fun waitForNonEmptyLiveData() : T?{
        val completableDeferred = CompletableDeferred<T>()
        Napier.d("$logPrefix waiting for non empty live data.")
        val observer = object: DoorObserver<T> {
            override fun onChanged(t: T) {
                if(t is List<*> && t.isNotEmpty()) {
                    completableDeferred.complete(t)
                }else if(t !is List<*> && t != null){
                    completableDeferred.complete(t)
                }
            }
        }

        var nonEmptyVal: T? = null
        withContext(doorMainDispatcher()) {
            autoRetryOnEmptyLiveData?.observeForever(observer)
            nonEmptyVal = withTimeoutOrNull(500) { completableDeferred.await()}
            autoRetryOnEmptyLiveData?.removeObserver(observer)
        }

        Napier.d({"$logPrefix Finished waiting for non empty live data. Result=$nonEmptyVal."})

        return nonEmptyVal
    }

    companion object {

        private const val PREFIX_NOCONNECTION_NO_MIRRORS_MESSAGE = "LoadHelper-NOCONNECTION"

        //val ID_ATOMICINT = atomic(0)

        const val STATUS_NOT_STARTED = 0

        const val STATUS_LOADING_CLOUD = 1

        const val STATUS_LOADING_MIRROR = 2

        const val STATUS_LOADED_WITHDATA = 11

        const val STATUS_LOADED_NODATA = 12

        const val STATUS_FAILED_NOCONNECTIVITYORPEERS = 15

        const val STATUS_FAILED_CONNECTION_ERR = 16

    }
}