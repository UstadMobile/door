package com.ustadmobile.door

import com.ustadmobile.door.ext.concurrentSafeListOf

/**
 * This class is used on Android and generated JDBC implementations. It manages adding and removing listeners, and helps
 * fire events.
 */
class IncomingReplicationListenerHelper {

    private val incomingListeners = concurrentSafeListOf<IncomingReplicationListener>()

    suspend fun fireIncomingReplicationEvent(evt: IncomingReplicationEvent) {
        incomingListeners.forEach {
            it.onIncomingReplicationProcessed(evt)
        }
    }

    fun addIncomingReplicationListener(listener: IncomingReplicationListener) {
        incomingListeners += listener
    }

    fun removeIncomingReplicationListener(listener: IncomingReplicationListener) {
        incomingListeners -= listener
    }

}