package com.ustadmobile.door

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import com.ustadmobile.door.RepositoryConnectivityListener
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.lang.ref.WeakReference
import io.github.aakira.napier.Napier
import kotlin.reflect.KClass

/**
 * This implements common repository functions such as addMirror, removeMirror, setMirrorPriority
 * setConnectivityStatus and val connectivityStatus
 */
class RepositoryHelper() {

    private val connectivityStatusAtomic = AtomicInteger(0)

    private val weakConnectivityListeners: MutableList<WeakReference<RepositoryConnectivityListener>> = CopyOnWriteArrayList()

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


    fun addWeakConnectivityListener(listener: RepositoryConnectivityListener) {
        weakConnectivityListeners.add(WeakReference(listener))
    }

    fun removeWeakConnectivityListener(listener: RepositoryConnectivityListener) {
        weakConnectivityListeners.removeAll { it.get() == listener }
    }

    fun <T : Any> addSyncListener(entityClass: KClass<T>, listener: SyncListener<T>)  {
        syncListeners.getOrPut(entityClass) { mutableListOf<SyncListener<out Any>>() }.add(listener)
    }

    fun <T: Any> removeSyncListener(entityClass: KClass<T>, listener: SyncListener<T>) {
        syncListeners.get(entityClass)?.remove(listener)
    }

    fun <T: Any> handleSyncEntitiesReceived(entityClass: KClass<T>, entities: List<T>)  {
        val event = SyncEntitiesReceivedEvent(entityClass, entities)
        (syncListeners.get(entityClass) as? List<SyncListener<T>>)?.forEach {
            it.onEntitiesReceived(event)
        }
    }


}