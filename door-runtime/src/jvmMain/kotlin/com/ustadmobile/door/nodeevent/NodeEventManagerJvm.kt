package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.ext.dbType
import com.ustadmobile.door.message.DoorMessageCallback
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.room.RoomJdbcImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * @param db this MUST be the unwrapped DB
 */
class NodeEventManagerJvm<T: RoomDatabase>(
    db: T,
    messageCallback: DoorMessageCallback<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : NodeEventManagerCommon<T>(
    db, messageCallback, dispatcher,
) {

    /**
     * Used on SQLite - where we pick up events using the jdbcimplhelper.
     */
    private val jdbcImplListener = NodeEventJdbcImplListenerSqlite(hasOutgoingReplicationTable, _outgoingEvents)

    init {
        //If using SQLite on JDBC, we need to add a listener to setup
        if(db.dbType() == DoorDbType.SQLITE) {
            (db as RoomJdbcImpl).jdbcImplHelper.addListener(jdbcImplListener)
        }else {
            //Setup listening for postgres channel
        }
    }
    
    override fun close() {
        if(db.dbType() == DoorDbType.SQLITE) {
            (db as RoomJdbcImpl).jdbcImplHelper.removeListener(jdbcImplListener)
        }
    }





}