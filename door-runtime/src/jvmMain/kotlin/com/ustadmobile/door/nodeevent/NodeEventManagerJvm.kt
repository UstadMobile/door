package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.ext.dbType
import com.ustadmobile.door.message.DoorMessageCallback
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.room.RoomJdbcImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * @param db this MUST be the unwrapped DB e.g. the JdbcImpl or Room implementation
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
    private val sqliteJdbcListener = if(db.dbType() == DoorDbType.SQLITE) {
        NodeEventJdbcImplListenerSqlite(hasOutgoingReplicationTable, _outgoingEvents)
    }else {
        null
    }

    private val postgresEventListener = if(db.dbType() == DoorDbType.POSTGRES) {
        PostgresNodeEventListener(
            dataSource = (db as DoorDatabaseJdbc).dataSource,
            outgoingEvents = _outgoingEvents,
            hasOutgoingReplicationTable = hasOutgoingReplicationTable,
        )
    }else {
        null
    }

    init {
        //If using SQLite on JDBC, we need to add a listener to setup
        sqliteJdbcListener?.also {
            (db as RoomJdbcImpl).jdbcImplHelper.addListener(it)
        }
    }
    
    override fun close() {
        sqliteJdbcListener?.also {
            (db as RoomJdbcImpl).jdbcImplHelper.removeListener(it)
        }

        postgresEventListener?.close()
    }





}