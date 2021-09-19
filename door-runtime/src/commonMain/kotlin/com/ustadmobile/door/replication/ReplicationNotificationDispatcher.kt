package com.ustadmobile.door.replication

import com.ustadmobile.door.TableChangeListener

/*

1. Generated DbName_ReplicatedEntityChangeListener will listen for changes on the repository. Where a Replicated Entity
is changed, it will run the annotated functions (e.g that insert into the tracker table) and trigger handleTableChanged
for the tracker table.

2. The replicationnotificationdispatcher will be accessible through the DI. It will run queries that determine which
nodes have been affected. It will then fire ReplicationPendingEvent

3. Those listening for ReplicationPendingEvent (e.g. Server Sent Event endpoint and SubscriptionManager) will act upon it.
SubscriptionManager update its status (leading to a sendPendingReplicationCall) and a server sent event endpoint will
sent an SSE.

 */
/**
 *
 *
 *
 * This class listens for newly generated replications using a TableChangeListener. It will be stored in
 * the DI. The generated DbName_ReplicatedEntityChangeListener will run annotated functions, and it will then trigger
 *
 *
 * It will use queries to determine the affected nodes. It will be made accessible in the DI, so that it can be used
 * by a ReplicationSubscriptionManager or a Server Sent Event http endpoint.
 *
 * It will then fire ReplicationNotificationEvent
 */


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