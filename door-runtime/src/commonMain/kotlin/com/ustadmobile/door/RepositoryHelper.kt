package com.ustadmobile.door

import kotlinx.coroutines.*
import com.ustadmobile.door.ext.concurrentSafeListOf
import kotlinx.atomicfu.atomic

/**
 * This implements common repository functions such as addMirror, removeMirror, setMirrorPriority
 * setConnectivityStatus and val connectivityStatus
 */
class RepositoryHelper() {

    private val connectivityStatusAtomic = atomic(0)

    private val connectivityListeners: MutableList<RepositoryConnectivityListener> = concurrentSafeListOf()

    var connectivityStatus: Int
        get() = connectivityStatusAtomic.value
        set(newValue) {
            connectivityStatusAtomic.value = newValue
            connectivityListeners.forEach {
                try {
                    /**
                     * There could be repo-backed livedata that is waiting to try and re-run.
                     * This might cause exceptions if the server itself is off even if connectivity
                     * is back, or if the connectivity is not great. Hence this is wrapped in
                     * try-catch
                     */
                    it.onConnectivityStatusChanged(newValue)
                }catch(e: Exception) {
                    println("Exception with weakConnectivityListener $e")
                }
            }
        }


    fun addWeakConnectivityListener(listener: RepositoryConnectivityListener) {
        connectivityListeners.add(listener)
    }

    fun removeWeakConnectivityListener(listener: RepositoryConnectivityListener) {
        connectivityListeners.remove(listener)
    }


}