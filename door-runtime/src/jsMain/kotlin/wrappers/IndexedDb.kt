package wrappers

import kotlinx.coroutines.CompletableDeferred

object IndexedDb{

    /**
     * Check if IndexedDb database exists with data store key of the SQL database
     * @param dbName Name of the database to be checked on
     */
    suspend fun checkIfExists(dbName: String): Boolean {
        val checkCompletable = CompletableDeferred<Boolean>()
        val request = indexedDb.open(dbName, DATABASE_VERSION)
        request.onupgradeneeded = { event: dynamic ->
            val db = event.target.result
            if (!db.objectStoreNames.contains(DB_STORE_NAME)){
                db.createObjectStore(DB_STORE_NAME)
            }
        }
        request.onerror = {
            checkCompletable.completeExceptionally(
                Throwable("Error when opening database"))
        }
        request.onsuccess = { event: dynamic ->
            val db = event.target.result
            val store = db.transaction(DB_STORE_NAME, "readwrite").objectStore(DB_STORE_NAME).get(DB_STORE_KEY)
            store.onsuccess = { data: dynamic ->
                checkCompletable.complete(data.target.result != null)
            }
            store.onerror = {
                checkCompletable.completeExceptionally(
                    Throwable("Error when querying for database from $DB_STORE_NAME"))
            }
        }
        return checkCompletable.await()
    }

    val indexedDb = js("window.indexedDB || window.mozIndexedDB || window.webkitIndexedDB || window.msIndexedDB")

    const val DB_STORE_NAME = "um_db_store"

    const val DB_STORE_KEY = "um_db_key"

    const val DATABASE_VERSION = 1
}