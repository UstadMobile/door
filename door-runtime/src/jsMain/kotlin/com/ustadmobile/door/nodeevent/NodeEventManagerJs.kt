package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.log.DoorLogger
import com.ustadmobile.door.message.DoorMessageCallback
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.room.RoomJdbcImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class NodeEventManagerJs<T: RoomDatabase>(
    db: T,
    messageCallback: DoorMessageCallback<T>,
    logger: DoorLogger,
    dbName: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : NodeEventManagerCommon<T>(
    db, messageCallback, logger, dbName, dispatcher,
) {

    private val sqliteJdbcListener = NodeEventJdbcImplListenerSqlite(
        hasOutgoingReplicationTable = hasOutgoingReplicationTable,
        outgoingEvents = _outgoingEvents,
        createTmpEvtTableAndTriggerOnBeforeTransaction = false,
        logger = logger,
        dbName = dbName,
    )

    init {
        (db as RoomJdbcImpl).jdbcImplHelper.addListener(sqliteJdbcListener)
    }

    override fun close() {
        (db as RoomJdbcImpl).jdbcImplHelper.removeListener(sqliteJdbcListener)
        super.close()
    }
}