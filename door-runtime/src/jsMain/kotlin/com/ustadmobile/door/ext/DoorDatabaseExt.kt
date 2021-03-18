package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.Worker
import wrappers.IndexedDb.checkIfExists
import wrappers.SqliteWorkerManager

/**
 * Init work that will only really be done for the real database implementation class (not the repo, syncreadonlywrapper, etc)
 */
fun DoorDatabase.init(dbName: String) {
    workerManager = SqliteWorkerManager(dbName, Worker("./worker.sql-wasm.js"))
    GlobalScope.launch {
        val exists = checkIfExists(dbName)
        if(exists){
            workerManager.loadDbFromIndexedDb()
        }else{
            createAllTables()
        }
        initCompletable.complete(true)
    }
}
