package com.ustadmobile.door

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import com.ustadmobile.door.sqljsjdbc.SQLiteDatasourceJs
import com.ustadmobile.door.util.DoorEventCollator

/**
 * This class is used to listen for all database changes and persist database to the indexedDB
 * after some delays.
 */
class SaveToIndexedDbChangeListener(
    database: DoorDatabase,
    private val datasource: SQLiteDatasourceJs,
    tablesToListen: List<String>,
    maxWaitTime: Long
) {
    private val changeListenerRequest: ChangeListenerRequest

    private val eventCollator = DoorEventCollator<List<String>>(maxWaitTime, GlobalScope) {
        datasource.saveDatabaseToIndexedDb()
    }

    init {
        changeListenerRequest = ChangeListenerRequest(tablesToListen){
            eventCollator.receiveEvent(it)
        }
        database.addChangeListener(changeListenerRequest)
    }

}