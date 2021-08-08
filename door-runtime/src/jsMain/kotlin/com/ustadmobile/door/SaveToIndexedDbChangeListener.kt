package com.ustadmobile.door

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import wrappers.SQLiteDatasourceJs

/**
 * This class is used to listen for all database changes and persist database to the indexedDB
 * after some delays.
 */
class SaveToIndexedDbChangeListener(
    database: DoorDatabase,
    private val datasource: SQLiteDatasourceJs,
    private val maxWaitTime: Long
) {

    private val changeListenerRequest: ChangeListenerRequest

    private var persistDbJob: Job? = null

    init {
        changeListenerRequest = ChangeListenerRequest(listOf(), this::onTablesChanged)
        database.addChangeListener(changeListenerRequest)
    }

    private fun onTablesChanged(tablesChanged: List<String>) {
        if(persistDbJob == null){
            persistDbJob = GlobalScope.async {
                delay(maxWaitTime)
                val saved = datasource.saveDatabaseToIndexedDb()
                if(saved){
                    persistDbJob = null
                }
            }
        }
    }
}