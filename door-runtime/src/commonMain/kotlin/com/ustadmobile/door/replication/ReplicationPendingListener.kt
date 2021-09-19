package com.ustadmobile.door.replication

/**
 * This interface will be implemented by a SubscriptionManager and ServerSentEvents endpoint
 *
 * Repository needs addReplicationNotificationListener and removeReplicationNotificationListener
 *
 */
fun interface ReplicationPendingListener {

    fun onReplicationPending(event: ReplicationPendingEvent)

}
