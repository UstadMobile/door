package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ext.*
import com.ustadmobile.door.room.RoomDatabaseJdbcImplHelperCommon
import com.ustadmobile.door.util.TransactionMode
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Used to listen for NodeEvent on JVM/SQLite and JS
 */
internal class NodeEventJdbcImplListenerSqlite(
    private val hasOutgoingReplicationTable: Boolean,
    private val outgoingEvents: MutableSharedFlow<List<NodeEvent>>,
): RoomDatabaseJdbcImplHelperCommon.Listener {

    private val pendingEvents = concurrentSafeMapOf<Int, List<NodeEvent>>()

    override suspend fun onBeforeTransactionAsync(
        transactionMode: TransactionMode,
        connection: Connection,
        transactionId: Int,
    ) {
        connection.createStatement().useStatementAsync { stmt ->
            stmt.executeUpdateAsync(NodeEventConstants.CREATE_EVENT_TMP_TABLE_SQL)
            stmt.takeIf { hasOutgoingReplicationTable }?.executeUpdateAsync(
                NodeEventConstants.CREATE_OUTGOING_REPLICATION_EVENT_TRIGGER
            )
        }
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