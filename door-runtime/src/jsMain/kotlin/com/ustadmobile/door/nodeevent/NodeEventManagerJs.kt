package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.message.DoorMessageCallback
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.room.RoomJdbcImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class NodeEventManagerJs<T: RoomDatabase>(
    db: T,
    messageCallback: DoorMessageCallback<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : NodeEventManagerCommon<T>(
    db, messageCallback, dispatcher,
) {

    private val sqliteJdbcListener = NodeEventJdbcImplListenerSqlite(
        hasOutgoingReplicationTable = hasOutgoingReplicationTable,
        outgoingEvents = _outgoingEvents,
        createTmpEvtTableAndTriggerOnBeforeTransaction = false
    )

    init {
        (db as RoomJdbcImpl).jdbcImplHelper.addListener(sqliteJdbcListener)
    }

    override fun close() {
        (db as RoomJdbcImpl).jdbcImplHelper.removeListener(sqliteJdbcListener)
        super.close()
    }
}