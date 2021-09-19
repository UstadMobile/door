package com.ustadmobile.door.replication

import com.ustadmobile.door.TableChangeListener

/**
 * 1) ReplicationNotificationDispatcher will listen to the database for table invalidations
 * 2) ReplicationNotificationDispatcher calls the generated ReplicationRunOnChangeRunner to run any queries that are annotated @ReplicationRunOnChange
 * 3) ReplicationNotificationDispatcher runs queries to determine which nodes need to know about changes
 * 4) ReplicationNotificationDispatcher fires ReplicationPendingEvent. That is received by the SubscriptionManager and/or ServerSentEvents endpoint.
 */
class ReplicationNotificationDispatcher(
    /**
     * The generated DbName_ReplicationRunOnChangeRunner
     */
    private val replicationRunOnChangeRunner: ReplicationRunOnChangeRunner

) : TableChangeListener{

    override fun onTableChanged(tableName: String) {
        //TODO: collate events and then fire replicationRunOnChangeListener.runReplicationRunOnChange
    }

    /**
     * This is used by ReplicationSubscriptionManager (to trigger sending changes). This will also trigger events if
     * there are any pending replication tracker entities for the given node.
     */
    suspend fun addReplicationPendingEventListener(listener: ReplicationPendingListener) {

    }

    fun removeReplicationPendingEventListener(listener: ReplicationPendingListener) {

    }
}