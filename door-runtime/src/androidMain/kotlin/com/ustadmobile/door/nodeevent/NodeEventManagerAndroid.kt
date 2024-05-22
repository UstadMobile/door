package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.entities.OutgoingReplication
import com.ustadmobile.door.ext.prepareAndUseStatementAsync
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp
import com.ustadmobile.door.jdbc.ext.mapRows
import com.ustadmobile.door.log.DoorLogger
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.message.DoorMessageCallback
import com.ustadmobile.door.room.InvalidationTrackerObserver
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.util.TransactionMode
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * Note: On Android the NodeEvent trigger is not used because it is not so straightforward to hook into the end of
 * each transaction. NodeEventManager on Android currently simply listens for Invalidation of OutgoingReplication. It
 * tracks the most recent uid to avoid emitting duplicate events.
 */
class NodeEventManagerAndroid<T: RoomDatabase>(
    db: T,
    messageCallback: DoorMessageCallback<T>,
    logger: DoorLogger,
    dbName: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : NodeEventManagerCommon<T>(
    db, messageCallback, logger, dbName, dispatcher
){

    private val scope = CoroutineScope(dispatcher + Job())

    private var lastOutgoingReplicationUid = 0L

    private val notifyChannel = Channel<Unit>(capacity = 1)

    private val invalidationObserver = object: InvalidationTrackerObserver("OutgoingReplication") {
        override fun onInvalidated(tables: Set<String>) {
            notifyChannel.trySend(Unit)
        }
    }

    init {
        db.invalidationTracker.takeIf { hasOutgoingReplicationTable }?.addObserver(invalidationObserver)
        scope.launch {
            runCheckForNewEventsLoop()
        }
    }


    private suspend fun CoroutineScope.runCheckForNewEventsLoop() {
        while(isActive) {
            notifyChannel.receive()
            val newOutgoingReplication = db.withDoorTransactionAsync(TransactionMode.READ_ONLY) {
                db.prepareAndUseStatementAsync(
                    sql = """
                            SELECT OutgoingReplication.*
                              FROM OutgoingReplication
                             WHERE OutgoingReplication.orUid >= ?
                          ORDER BY OutgoingReplication.orUid ASC           
                        """,
                    readOnly = true
                ) { stmt ->
                    stmt.setLong(1, lastOutgoingReplicationUid)
                    stmt.executeQueryAsyncKmp().use { results ->
                        results.mapRows { resultSet ->
                            OutgoingReplication(
                                orUid = resultSet.getLong("orUid"),
                                destNodeId = resultSet.getLong("destNodeId"),
                                orTableId = resultSet.getInt("orTableId"),
                                orPk1 = resultSet.getLong("orPk1"),
                                orPk2 = resultSet.getLong("orPk2"),
                                orPk3 = resultSet.getLong("orPk3"),
                                orPk4 = resultSet.getLong("orPk4")
                            )
                        }
                    }
                }
            }

            if(newOutgoingReplication.isNotEmpty()) {
                lastOutgoingReplicationUid = newOutgoingReplication.last().orUid
            }

            _outgoingEvents.emit(newOutgoingReplication.map {
                NodeEvent(
                    what = DoorMessage.WHAT_REPLICATION_PUSH,
                    toNode = it.destNodeId,
                    tableId = it.orTableId,
                    key1 = it.orPk1,
                    key2 = it.orPk2,
                    key3 = it.orPk3,
                    key4 = it.orPk4,
                )
            })
        }
    }

    override fun close() {
        db.invalidationTracker.takeIf { hasOutgoingReplicationTable }?.removeObserver(invalidationObserver)
        scope.cancel()
        super.close()
    }
}