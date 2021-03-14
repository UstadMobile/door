package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.Worker

/**
 * Init work that will only really be done for the real database implementation class (not the repo, syncreadonlywrapper, etc)
 */
fun DoorDatabase.init(dbName: String) {
    worker = Worker("sqllite.wasm.js")
    GlobalScope.launch {
        //do the setup tasks: e.g. check for data on IndexedDb, createTables, run migrations, etc.

        val indexedDb = js("window.indexedDB || window.mozIndexedDB || window.webkitIndexedDB || window.msIndexedDB")
        val request = indexedDb.open("UmDatabase", 1)
        //somehow async, we would wind up with a TypedArray

        initCompletable.complete(true)
    }
}
