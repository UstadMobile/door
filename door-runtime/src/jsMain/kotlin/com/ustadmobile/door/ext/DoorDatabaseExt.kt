package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import doDbExist
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import loadDbFromIndexedDb
import org.w3c.dom.Worker

/**
 * Init work that will only really be done for the real database implementation class (not the repo, syncreadonlywrapper, etc)
 */
fun DoorDatabase.init(dbName: String, version:Int = 1) {
    worker = Worker("worker.sql-wasm.js")
    GlobalScope.launch {
        val exists = doDbExist(dbName, version)
        if(exists){
            loadDbFromIndexedDb(dbName, version, worker)
        }else{
            createAllTables()
        }
        initCompletable.complete(true)
    }
}
