package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.TablesInvalidationListener
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp
import com.ustadmobile.door.util.DoorEventCollator
import kotlinx.coroutines.CoroutineScope

/**
 * 1) ReplicationNotificationDispatcher will listen to the database for table invalidations
 * 2) ReplicationNotificationDispatcher calls the generated ReplicationRunOnChangeRunner to run any queries that are annotated @ReplicationRunOnChange
 * 3) ReplicationNotificationDispatcher runs queries to determine which nodes need to know about changes
 * 4) ReplicationNotificationDispatcher fires ReplicationPendingEvent. That is received by the SubscriptionManager and/or ServerSentEvents endpoint.
 */
class ReplicationNotificationDispatcher(
    private val db: DoorDatabase,

    /**
     * The generated DbName_ReplicationRunOnChangeRunner
     */
    private val replicationRunOnChangeRunner: ReplicationRunOnChangeRunner,

    coroutineScope: CoroutineScope,

    private val dbMetaData: DoorDatabaseMetadata<*> = db::class.doorDatabaseMetadata()

) : TablesInvalidationListener {

    private data class ReplicationPendingRequest(val nodeId: Long, val listener: ReplicationPendingListener)

    //Init should run a query to find anything pending in ChangeLog and call onTableChanged to handle them

    private val eventCollator = DoorEventCollator(200, coroutineScope, this::onDispatch)

    private val replicationPendingListeners = concurrentSafeListOf<ReplicationPendingRequest>()

    override fun onTablesInvalidated(tableNames: List<String>) {
        eventCollator.receiveEvent(tableNames)
    }

    suspend fun onDispatch(event: List<List<String>>) {
        val changedTables = event.flatten().toSet()
        val replicationsToCheck = replicationRunOnChangeRunner.runReplicationRunOnChange(changedTables)
        replicationsToCheck.forEach { tableName ->
            val repMetaData = dbMetaData.replicateEntities.values
                .firstOrNull { it.entityTableName == tableName } ?: return@forEach

            //Note: this might need to be batched (e.g. use LIMIT/OFFSET)
            val sql = """
                SELECT DISTINCT ${repMetaData.trackerDestNodeIdFieldName} 
                  FROM ${repMetaData.trackerTableName}
                 WHERE CAST(${repMetaData.trackerProcessedFieldName} AS BOOLEAN) = FALSE  
            """

            db.prepareAndUseStatementAsync(sql) { stmt ->
                stmt.executeQueryAsyncKmp().useResults { result ->
                    val devicesToNotify = result.mapRows { it.getLong(1) }
                    devicesToNotify.forEach { deviceId ->
                        fire(ReplicationPendingEvent(deviceId, listOf(repMetaData.tableId)))
                    }
                }
            }
        }

        db::class.doorDatabaseMetadata()
    }

    private fun fire(evt: ReplicationPendingEvent) {
        replicationPendingListeners.filter { it.nodeId == evt.nodeId }.forEach {
            it.listener.onReplicationPending(evt)
        }
    }

    /**
     * This is used by ReplicationSubscriptionManager (to trigger sending changes). This will also trigger events if
     * there are any pending replication tracker entities for the given node.
     */
    @Suppress("RedundantSuspendModifier")
    suspend fun addReplicationPendingEventListener(nodeId: Long, listener: ReplicationPendingListener) {
        //TODO: run a query and find pending replications for this node (if any)

        replicationPendingListeners.add(ReplicationPendingRequest(nodeId, listener))
    }

    fun removeReplicationPendingEventListener(nodeId: Long, listener: ReplicationPendingListener) {
        replicationPendingListeners.remove(ReplicationPendingRequest(nodeId, listener))
    }
}