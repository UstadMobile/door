package com.ustadmobile.door

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import com.ustadmobile.door.RepositoryConnectivityListener
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.lang.ref.WeakReference
import com.github.aakira.napier.Napier
import kotlin.reflect.KClass

/**
 * This implements common repository functions such as addMirror, removeMirror, setMirrorPriority
 * setConnectivityStatus and val connectivityStatus
 */
class RepositoryHelper(private val coroutineDispatcher: CoroutineDispatcher = doorMainDispatcher()) {

    private val mirrors: MutableMap<Int, MirrorEndpoint> = ConcurrentHashMap()

    private val nextMirrorId = AtomicInteger(1)

    private val connectivityStatusAtomic = AtomicInteger(0)

    private val weakConnectivityListeners: MutableList<WeakReference<RepositoryConnectivityListener>> = CopyOnWriteArrayList()

    private val tableChangeListeners: MutableList<TableChangeListener> = CopyOnWriteArrayList()

    private val syncListeners: MutableMap<KClass<out Any>, MutableList<SyncListener<out Any>>> = ConcurrentHashMap()

    var connectivityStatus: Int
        get() = connectivityStatusAtomic.get()
        set(newValue) {
            connectivityStatusAtomic.set(newValue)
            weakConnectivityListeners.forEach {
                try {
                    /**
                     * There could be repo-backed livedata that is waiting to try and re-run.
                     * This might cause exceptions if the server itself is off even if connectivity
                     * is back, or if the connectivity is not great. Hence this is wrapped in
                     * try-catch
                     */
                    it?.get()?.onConnectivityStatusChanged(newValue)
                }catch(e: Exception) {
                    println("Exception with weakConnectivityListener $e")
                }
            }
        }

    suspend fun addMirror(mirrorEndpoint: String, initialPriority: Int) = withContext(coroutineDispatcher){
        val newMirror = MirrorEndpoint(nextMirrorId.incrementAndGet(), mirrorEndpoint, initialPriority)
        mirrors[newMirror.mirrorId] = newMirror
        Napier.i("RepositoryHelper: New mirror added #${newMirror.mirrorId} - $mirrorEndpoint")

        weakConnectivityListeners.forEach {
            it.get()?.onNewMirrorAvailable(newMirror)
        }

        newMirror.mirrorId
    }

    suspend fun removeMirror(mirrorId: Int) = withContext(coroutineDispatcher) {
        mirrors.remove(mirrorId)
    }

    suspend fun updateMirrorPriorities(newPriorities: Map<Int, Int>) = withContext(coroutineDispatcher) {
        newPriorities.forEach {
            mirrors[it.key]?.priority = it.value
        }
    }

    suspend fun activeMirrors() = mirrors.values.toList()


    fun addWeakConnectivityListener(listener: RepositoryConnectivityListener) {
        weakConnectivityListeners.add(WeakReference(listener))
    }

    fun removeWeakConnectivityListener(listener: RepositoryConnectivityListener) {
        weakConnectivityListeners.removeAll { it.get() == listener }
    }

    fun addTableChangeListener(listener: TableChangeListener) {
        tableChangeListeners += listener
    }

    fun removeTableChangeListener(listener: TableChangeListener) {
        tableChangeListeners -= listener
    }

    fun handleTableChanged(tableName: String) {
        tableChangeListeners.forEach {
            //TODO: Call the update function to mark this table as having been changed.
            it.onTableChanged(tableName)
        }
    }

    fun <T : Any> addSyncListener(entityClass: KClass<T>, listener: SyncListener<T>)  {
        syncListeners.getOrPut(entityClass) { mutableListOf<SyncListener<out Any>>() }.add(listener)
    }

    fun <T: Any> handleSyncEntitiesReceived(entityClass: KClass<T>, entities: List<T>)  {
        (syncListeners.get(entityClass) as? List<SyncListener<T>>)?.forEach {
            it.onEntitiesReceived(entities)
        }
    }


}