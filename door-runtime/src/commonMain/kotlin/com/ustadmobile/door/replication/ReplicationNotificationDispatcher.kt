package com.ustadmobile.door.replication

import com.ustadmobile.door.ChangeListenerRequest
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.TablesInvalidationListener
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp
import com.ustadmobile.door.util.DoorEventCollator
import com.ustadmobile.door.util.NodeIdAuthCache
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

    private val coroutineScope: CoroutineScope,

    private val dbMetaData: DoorDatabaseMetadata<*> = db::class.doorDatabaseMetadata()

) : TablesInvalidationListener, NodeIdAuthCache.OnNewDoorNode {

    private data class ReplicationPendingRequest(val nodeId: Long, val listener: ReplicationPendingListener)

    private val eventCollator = DoorEventCollator(200, coroutineScope, this::onDispatch)

    init {
        db.addInvalidationListener(ChangeListenerRequest(dbMetaData.replicateTableNames, this))
        coroutineScope.launch {
            val pendingChangeLogs = db.findDistinctPendingChangeLogs()
            Napier.d("ReplicationNotificationDispatcher for [$db] startup: found ${pendingChangeLogs.size} ChangeLogs to" +
                    " process now", tag = DoorTag.LOG_TAG)
            eventCollator.receiveEvent(pendingChangeLogs.mapNotNull { dbMetaData.replicateEntities[it]?.entityTableName })
        }
    }

    private val replicationPendingListeners = concurrentSafeListOf<ReplicationPendingRequest>()

    private val findAllTablesPendingReplicationByNodeIdSql: String by lazy {
        dbMetaData.replicateEntities.values.joinToString(separator = "\nUNION\n") { repEntity ->
            """
            SELECT ${repEntity.tableId} AS tableId
            WHERE EXISTS(
                  SELECT ${repEntity.trackerTableName}.${repEntity.trackerDestNodeIdFieldName}
                    FROM ${repEntity.trackerTableName}
                   WHERE ${repEntity.trackerDestNodeIdFieldName} = ?
                     AND CAST(${repEntity.trackerProcessedFieldName} AS INTEGER) = 0)
            """
        }
    }

    override fun onTablesInvalidated(tableNames: List<String>) {
        eventCollator.receiveEvent(tableNames)
    }

    override fun onNewDoorNode(newNodeId: Long, auth: String) {
        coroutineScope.launch {
            val tablesChanged = replicationRunOnChangeRunner.runOnNewNode(newNodeId)
            Napier.d("ReplicationNotificationDispatcher for [$db] - onNewDoorNode nodeId $newNodeId " +
                    "check for pending replications on table(s): ${tablesChanged.joinToString()}")
            findAndSendPendingReplicationNotifications(tablesChanged.toSet())
        }
    }

    /**
     * Connect with the given nodeIdAndAuthCache to receive
     */
    fun setupWithNodeIdAndAuthCache(nodeIdAuthCache: NodeIdAuthCache): ReplicationNotificationDispatcher {
        nodeIdAuthCache.addNewNodeListener(this)
        return this
    }

    private suspend fun onDispatch(event: List<List<String>>) {
        val changedTables = event.flatten().toSet()
        Napier.d("ReplicationNotificationDispatcher for [$db]: processing changes to ${changedTables.joinToString()}",
            tag = DoorTag.LOG_TAG)
        val replicationsToCheck = replicationRunOnChangeRunner.runReplicationRunOnChange(changedTables)
        findAndSendPendingReplicationNotifications(replicationsToCheck)
    }

    private suspend fun findAndSendPendingReplicationNotifications(changedTableNames: Set<String>) {
        Napier.d("ReplicationNotificationDispatcher for [$db] findAndSendPendingReplicationNotifications " +
                " for table(s) ${changedTableNames.joinToString()}")
        changedTableNames.forEach { tableName ->
            val repMetaData = dbMetaData.replicateEntities.values
                .firstOrNull { it.entityTableName == tableName } ?: return@forEach

            //Note: this might need to be batched (e.g. use LIMIT/OFFSET)
            val sql = """
                SELECT DISTINCT ${repMetaData.trackerDestNodeIdFieldName} 
                  FROM ${repMetaData.trackerTableName}
                 WHERE CAST(${repMetaData.trackerProcessedFieldName} AS INTEGER) = 0  
            """

            db.prepareAndUseStatementAsync(sql) { stmt ->
                stmt.executeQueryAsyncKmp().useResults { result ->
                    val devicesToNotify = result.mapRows{ it.getLong(1) }
                    Napier.d("ReplicationNotificationDispatcher for [$db]: sending notifications for changes to table " +
                            "${repMetaData.entityTableName} to nodes ${devicesToNotify.joinToString()}", tag = DoorTag.LOG_TAG)


                    devicesToNotify.forEach { deviceId ->
                        fire(ReplicationPendingEvent(deviceId, listOf(repMetaData.tableId)))
                    }
                }
            }
        }
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
        replicationPendingListeners.add(ReplicationPendingRequest(nodeId, listener))

        val pendingTableIds = db.prepareAndUseStatementAsync(findAllTablesPendingReplicationByNodeIdSql) { stmt ->
            for(i in 1 .. dbMetaData.replicateEntities.size) {
                stmt.setLong(i, nodeId)
            }

            stmt.executeQueryAsyncKmp().useResults {
                it.mapRows { it.getInt(1) }
            }
        }

        listener.takeIf { pendingTableIds.isNotEmpty() }
            ?.onReplicationPending(ReplicationPendingEvent(nodeId, pendingTableIds))
    }

    fun removeReplicationPendingEventListener(nodeId: Long, listener: ReplicationPendingListener) {
        replicationPendingListeners.remove(ReplicationPendingRequest(nodeId, listener))
    }
}