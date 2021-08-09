package com.ustadmobile.door

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import com.ustadmobile.door.sqljsjdbc.SQLiteDatasourceJs

/**
 * This class is used to listen for all database changes and persist database to the indexedDB
 * after some delays.
 */
class SaveToIndexedDbChangeListener(database: DoorDatabase, private val datasource: SQLiteDatasourceJs,
                                    private val delayTime: Long) {

    private val changeListenerRequest: ChangeListenerRequest

    private var persistDbJob: Job? = null

    init {
        changeListenerRequest = ChangeListenerRequest(listOf()){
            onTablesChanged()
        }
        database.addChangeListener(changeListenerRequest)
    }

    private fun onTablesChanged() {
        if(persistDbJob == null){
            persistDbJob = GlobalScope.async {
                delay(delayTime)
                val saved = datasource.saveDatabaseToIndexedDb()
                if(saved){
                    persistDbJob = null
                }
            }
        }
    }
}