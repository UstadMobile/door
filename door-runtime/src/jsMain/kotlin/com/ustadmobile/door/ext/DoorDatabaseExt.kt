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
        //TODO: Check if the given indexeddb exists, or not
        // if it does exist, load the database from the indexddb data
        // if it does not exist, call db's createAllTables function

        initCompletable.complete(true)
    }
}
