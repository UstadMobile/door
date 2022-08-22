package com.ustadmobile.door.replication

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp
import com.ustadmobile.door.jdbc.ext.mapRows
import com.ustadmobile.door.jdbc.ext.useResults
import com.ustadmobile.door.room.InvalidationTrackerObserver
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
    private val db: RoomDatabase,

    /**
     * The generated DbName_ReplicationRunOnChangeRunner
     */
    private val replicationRunOnChangeRunner: ReplicationRunOnChangeRunner,

    private val coroutineScope: CoroutineScope,

    private val dbMetaData: DoorDatabaseMetadata<*> = db::class.doorDatabaseMetadata()

) : InvalidationTrackerObserver(db::class.doorDatabaseMetadata().replicateTableNames.toTypedArray()),
    NodeIdAuthCache.OnNewDoorNode
{

    private data class ReplicationPendingRequest(val nodeId: Long, val listener: ReplicationPendingListener)

    private val eventCollator = DoorEventCollator(200, coroutineScope, this::onDispatch)

    init {
        db.getInvalidationTracker().addObserver(this)
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
                     AND CAST(${repEntity.trackerPendingFieldName} AS INTEGER) = 1)
            """
        }
    }

    override fun onInvalidated(tables: Set<String>) {
        eventCollator.receiveEvent(tables.toList())
    }

    override fun onNewDoorNode(newNodeId: Long, auth: String) {
        coroutineScope.launch {
            val tablesChanged = replicationRunOnChangeRunner.runOnNewNode(newNodeId)
            Napier.d("ReplicationNotificationDispatcher for [$db] - onNewDoorNode nodeId $newNodeId " +
                    "check for pending replications on table(s): ${tablesChanged.joinToString()}", tag = DoorTag.LOG_TAG)
            findAndSendPendingReplicationNotifications(tablesChanged.toSet())
        }
    }

    private suspend fun onDispatch(event: List<List<String>>) {
        val changedTables = event.flatten().toSet()
        Napier.d("ReplicationNotificationDispatcher for [$db]: processing changes to ${changedTables.joinToString()}",
            tag = DoorTag.LOG_TAG)
        val replicationsToCheck = replicationRunOnChangeRunner.runReplicationRunOnChange(changedTables)
        Napier.d("ReplicationNotificationDispatcher: findPendingReplications for ${replicationsToCheck.joinToString()}",
            tag = DoorTag.LOG_TAG)
        findAndSendPendingReplicationNotifications(replicationsToCheck)
    }

    private suspend fun findAndSendPendingReplicationNotifications(changedTableNames: Set<String>) {
        Napier.d("ReplicationNotificationDispatcher for [$db] findAndSendPendingReplicationNotifications " +
                " for table(s) ${changedTableNames.joinToString()}")

        val changesByDevice = mutableMapOf<Long, MutableList<Int>>()
        changedTableNames.forEach { tableName ->
            val repMetaData = dbMetaData.replicateEntities.values
                .firstOrNull { it.entityTableName == tableName } ?: return@forEach

            //Note: this might need to be batched (e.g. use LIMIT/OFFSET)
            val sql = """
                SELECT DISTINCT ${repMetaData.trackerDestNodeIdFieldName} 
                  FROM ${repMetaData.trackerTableName}
                 WHERE CAST(${repMetaData.trackerPendingFieldName} AS INTEGER) = 1 
            """

            db.prepareAndUseStatementAsync(sql) { stmt ->
                stmt.executeQueryAsyncKmp().useResults { result ->
                    val devicesToNotify = result.mapRows{ it.getLong(1) }
                    devicesToNotify.forEach { deviceId ->
                        changesByDevice.getOrPut(deviceId) { mutableListOf() }.add(repMetaData.tableId)
                    }
                }
            }
        }

        changesByDevice.forEach {
            fire(ReplicationPendingEvent(it.key, it.value))
            Napier.d("ReplicationNotificationDispatcher for [$db]: sending notifications for changes for tables ids " +
                            "${it.value.joinToString()}} to node id ${it.key}", tag = DoorTag.LOG_TAG)
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