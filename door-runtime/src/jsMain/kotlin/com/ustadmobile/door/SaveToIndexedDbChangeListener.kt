package com.ustadmobile.door

import com.ustadmobile.door.room.InvalidationTrackerObserver
import com.ustadmobile.door.room.RoomDatabase
import kotlinx.coroutines.GlobalScope
import com.ustadmobile.door.sqljsjdbc.SQLiteDatasourceJs
import com.ustadmobile.door.util.DoorEventCollator

/**
 * This class is used to listen for all database changes and persist database to the indexedDB
 * after some delays.
 */
class SaveToIndexedDbChangeListener(
    database: RoomDatabase,
    private val datasource: SQLiteDatasourceJs,
    tablesToListen: List<String>,
    maxWaitTime: Long
) {
    private val changeListenerRequest: InvalidationTrackerObserver

    private val eventCollator = DoorEventCollator<List<String>>(maxWaitTime, GlobalScope) {
        datasource.saveDatabaseToIndexedDb()
    }

    init {
        changeListenerRequest = object: InvalidationTrackerObserver(tablesToListen.toTypedArray())  {
            override fun onInvalidated(tables: Set<String>) {
                eventCollator.receiveEvent(tables.toList())
            }
        }
    }

}