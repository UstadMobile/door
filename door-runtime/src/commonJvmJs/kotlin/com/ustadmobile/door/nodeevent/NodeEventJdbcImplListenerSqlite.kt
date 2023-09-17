package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ext.*
import com.ustadmobile.door.room.RoomDatabaseJdbcImplHelperCommon
import com.ustadmobile.door.util.TransactionMode
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Used to listen for NodeEvent on JVM/SQLite and JS
 *
 * @param createTmpEvtTableAndTriggerOnBeforeTransaction if true, then a trigger will be created in onBeforeTransaction
 *        to create a temporary table for NodeEvent and a trigger to create a new NodeEvent. This is required on JVM/JDBC
 *        where any temporary trigger and table is per-connection. It is not required on Javascript.
 *
 */
internal class NodeEventJdbcImplListenerSqlite(
    private val hasOutgoingReplicationTable: Boolean,
    private val outgoingEvents: MutableSharedFlow<List<NodeEvent>>,
    private val createTmpEvtTableAndTriggerOnBeforeTransaction: Boolean = true,
): RoomDatabaseJdbcImplHelperCommon.Listener {

    private val pendingEvents = concurrentSafeMapOf<Int, List<NodeEvent>>()

    override suspend fun onBeforeTransactionAsync(
        transactionMode: TransactionMode,
        connection: Connection,
        transactionId: Int,
    ) {
        connection.takeIf { createTmpEvtTableAndTriggerOnBeforeTransaction }?.createNodeEventTmpTableAndTrigger(
            hasOutgoingReplicationTable)
    }

    override suspend fun onAfterTransactionAsync(
        transactionMode: TransactionMode,
        connection: Connection,
        transactionId: Int,
    ) {
        val events = connection.prepareStatement(
            NodeEventConstants.SELECT_EVENT_FROM_TMP_TABLE
        ).useStatementAsync { stmt ->
            stmt.executeQueryAsyncKmp().useResults { results ->
                results.mapRows {
                    NodeEvent(
                        what = it.getInt("what"),
                        toNode = it.getLong("toNode"),
                        tableId = it.getInt("tableId"),
                        key1 = it.getLong("key1"),
                        key2 = it.getLong("key2"),
                    )
                }
            }
        }

        connection.prepareStatement(
            NodeEventConstants.CLEAR_EVENTS_TMP_TABLE
        ).useStatementAsync { stmt ->
            stmt.executeUpdateAsyncKmp()
        }

        pendingEvents[transactionId] = events
    }

    override suspend fun onTransactionCommittedAsync(
        transactionMode: TransactionMode,
        connection: Connection,
        transactionId: Int,
    ) {
        pendingEvents.remove(transactionId)?.also {
            outgoingEvents.emit(it)
        }
    }

    override fun onBeforeTransaction(
        transactionMode: TransactionMode,
        connection: Connection,
        transactionId: Int,
    ) {

    }

    override fun onAfterTransaction(
        transactionMode: TransactionMode,
        connection: Connection,
        transactionId: Int,
    ) {

    }
}