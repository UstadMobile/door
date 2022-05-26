package com.ustadmobile.door.sqljsjdbc

import com.ustadmobile.door.ext.DoorTag
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CompletableDeferred
import org.w3c.files.Blob

/**
 * It might be worth considering:
 * https://github.com/JuulLabs/indexeddb
 */
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
            //Do we need to close this?
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

    suspend fun storeBlob(
        dbName: String,
        storeName: String,
        key: String,
        blob: Blob,
    ) {
        val request = indexedDb.open(dbName, DATABASE_VERSION)
        val completeable = CompletableDeferred<Boolean>()

        request.onupgradeneeded = { event: dynamic ->
            val db = event.target.result
            if (!db.objectStoreNames.contains(storeName)){
                db.createObjectStore(storeName)
            }
        }

        request.onsuccess = { event: dynamic ->
            val db = event.target.result
            val transaction = db.transaction(storeName, "readwrite")
            transaction.oncomplete = {
                Napier.d(tag = DoorTag.LOG_TAG) { "Saved blob to db" }
                completeable.complete(true)
            }

            transaction.onerror = {
                Napier.e(tag = DoorTag.LOG_TAG) { "Failed to save blob" }
                completeable.completeExceptionally(Exception("storeAttachment exception"))
            }

            val store = transaction.objectStore(storeName)
            store.put(blob, key)
        }

        completeable.await()
    }

    suspend fun retrieveBlob(
        dbName: String,
        storeName: String,
        key: String
    ): Blob? {
        val completableDeferred = CompletableDeferred<Blob?>()
        val request = indexedDb.open(dbName, DATABASE_VERSION)

        request.onupgradeneeded = { event: dynamic ->
            val db = event.target.result
            if (!db.objectStoreNames.contains(storeName)){
                db.createObjectStore(storeName)
            }
        }

        request.onsuccess = { event: dynamic ->
            val db = event.target.result
            val store = db.transaction(storeName, "readwrite").objectStore(storeName).get(key)
            store.onsuccess = { data: dynamic ->
                completableDeferred.complete(data.target.result.unsafeCast<Blob?>())
            }

            store.onerror = {
                val errMsg = "Exception attempting to retrieve blob: db=$dbName/store=$storeName/key=$key"
                Napier.e(errMsg, tag = DoorTag.LOG_TAG)
                completableDeferred.completeExceptionally(Exception(errMsg))
            }
        }

        return completableDeferred.await()
    }

    //TODO: Delete blob: https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/delete


    val indexedDb = js("window.indexedDB || window.mozIndexedDB || window.webkitIndexedDB || window.msIndexedDB")

    const val DB_STORE_NAME = "um_db_store"

    const val ATTACHMENT_STORE_NAME = "door_attachments"

    const val DB_STORE_KEY = "um_db_key"

    const val DATABASE_VERSION = 1
}