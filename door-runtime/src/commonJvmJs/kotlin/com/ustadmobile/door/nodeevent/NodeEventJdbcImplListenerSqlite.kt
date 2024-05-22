package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ext.*
import com.ustadmobile.door.log.DoorLogger
import com.ustadmobile.door.log.d
import com.ustadmobile.door.log.v
import com.ustadmobile.door.room.RoomDatabaseJdbcImplHelperCommon
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
    private val logger: DoorLogger,
    private val dbName: String,
): RoomDatabaseJdbcImplHelperCommon.Listener {

    private val logPrefix = "[NodeEventJdbcImplListenerSqlite - $dbName]"

    private val pendingEvents = concurrentSafeMapOf<Int, List<NodeEvent>>()

    override suspend fun onBeforeTransactionAsync(
        readOnly: Boolean,
        connection: Connection,
        transactionId: Int,
    ) {
        if(readOnly)
            return

        if(createTmpEvtTableAndTriggerOnBeforeTransaction) {
            logger.v { "$logPrefix creating SQLite triggers" }
            connection.createNodeEventTableAndTrigger(
                hasOutgoingReplicationTable = hasOutgoingReplicationTable
            )
        }
    }

    override suspend fun onAfterTransactionAsync(
        readOnly: Boolean,
        connection: Connection,
        transactionId: Int,
    ) {
        if(readOnly)
            return

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
                        key3 = it.getLong("key3"),
                        key4 = it.getLong("key4"),
                    )
                }
            }
        }

        logger.v { "$logPrefix found ${events.size} new events = ${events.joinToString()}" }

        connection.prepareStatement(
            NodeEventConstants.CLEAR_EVENTS_TMP_TABLE
        ).useStatementAsync { stmt ->
            stmt.executeUpdateAsyncKmp()
        }

        if(events.isNotEmpty()) {
            logger.d { "$logPrefix emitting ${events.size} events ${events.joinToString()} "}
            pendingEvents[transactionId] = events
        }
    }

    override suspend fun onTransactionCommittedAsync(
        readOnly: Boolean,
        connection: Connection,
        transactionId: Int,
    ) {
        if(readOnly)
            return

        pendingEvents.remove(transactionId)?.also {
            outgoingEvents.emit(it)
        }
    }

    override fun onBeforeTransaction(
        readOnly: Boolean,
        connection: Connection,
        transactionId: Int,
    ) {

    }

    override fun onAfterTransaction(
        readOnly: Boolean,
        connection: Connection,
        transactionId: Int,
    ) {

    }
}