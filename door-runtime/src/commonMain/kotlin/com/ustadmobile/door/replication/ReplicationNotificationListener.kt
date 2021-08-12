package com.ustadmobile.door.replication

/**
 * This interface will be implemented by a SubscriptionManager and ServerSentEvents endpoint
 */
fun interface ReplicationNotificationListener {

    fun onReplicationNotification(event: ReplicationPendingEvent)

}
