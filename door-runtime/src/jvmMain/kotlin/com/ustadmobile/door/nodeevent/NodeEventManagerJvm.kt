package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.ext.dbType
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp
import com.ustadmobile.door.jdbc.ext.executeUpdateAsync
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.room.RoomDatabaseJdbcImplHelper
import com.ustadmobile.door.room.RoomJdbcImpl
import com.ustadmobile.door.util.TransactionMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import com.ustadmobile.door.jdbc.ext.useStatementAsync
import com.ustadmobile.door.jdbc.ext.useResults
import com.ustadmobile.door.jdbc.ext.mapRows
import com.ustadmobile.door.room.RoomDatabaseJdbcImplHelperCommon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll

class NodeEventManagerJvm(
    db: RoomDatabase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    //scope: CoroutineScope,
) : NodeEventManagerCommon(
    db, dispatcher, //scope
) {

    /**
     * Used on SQLite - where we pickup events using the jdbcimplhelper.
     */
    private val jdbcImplListener = object: RoomDatabaseJdbcImplHelperCommon.Listener {

        private val pendingEvents = concurrentSafeMapOf<Int, List<NodeEvent>>()

        override suspend fun onBeforeTransactionAsync(
            transactionMode: TransactionMode,
            connection: Connection,
            transactionId: Int,
        ) {
            println("Creating replication event temp tables and trigger")
            connection.createStatement().useStatementAsync { stmt ->
                stmt.executeUpdateAsync(NodeEventConstants.CREATE_EVENT_TMP_TABLE_SQL)
                stmt.executeUpdateAsync(NodeEventConstants.CREATE_OUTGOING_REPLICATION_EVENT_TRIGGER)
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
            println("onAfterTransactionAsync: found ${events.size} events")

            pendingEvents[transactionId] = events
        }

        override suspend fun onTransactionCommittedAsync(
            transactionMode: TransactionMode,
            connection: Connection,
            transactionId: Int,
        ) {
            println("Emitting events")
            pendingEvents.remove(transactionId)?.also {
                _outgoingEvents.emitAll(it.asFlow())
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

    init {
        //If using SQLite on JDBC, we need to add a listener to setup
        if(db.dbType() == DoorDbType.SQLITE) {
            println("Adding JDBC implementation listener")
            (db as RoomJdbcImpl).jdbcImplHelper.addListener(jdbcImplListener)
        }else {
            //Setup listening for postgres channel
        }
    }
    
    fun close() {
        if(db.dbType() == DoorDbType.SQLITE) {
            (db as RoomJdbcImpl).jdbcImplHelper.removeListener(jdbcImplListener)
        }
    }





}