package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.Worker
import wrappers.IndexedDb.checkIfExists
import wrappers.SQLiteDatasourceJs

/**
 * Init work that will only really be done for the real database implementation class (not the repo, syncreadonlywrapper, etc)
 */
fun DoorDatabase.init(dbName: String, webWorkerPath: String) {
    dataSource = SQLiteDatasourceJs(dbName, Worker(webWorkerPath))
    GlobalScope.launch {
        val exists = checkIfExists(dbName)
        if(exists){
            dataSource.loadDbFromIndexedDb()
        }else{
            createAllTables()
        }
        initCompletable.complete(true)
    }
}
