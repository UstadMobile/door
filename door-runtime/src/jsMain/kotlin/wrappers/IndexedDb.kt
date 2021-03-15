import kotlinx.coroutines.CompletableDeferred
import org.w3c.dom.Worker
import kotlin.js.json


private val indexedDb = js("window.indexedDB || window.mozIndexedDB || window.webkitIndexedDB || window.msIndexedDB")

private const val DB_STORE_NAME = "um_db_store"

private const val DB_STORE_KEY = "um_db_key"

const val ACTION_DB_OPEN = 11

const val ACTION_SQL_EXPORT = 12

suspend fun doDbExist(dbName: String, version: Int): Boolean {
    val checkCompletable = CompletableDeferred<Boolean>()
    val request = indexedDb.open(dbName, version)
    request.onupgradeneeded = { event: dynamic ->
        val db = event.target.result
        if (!db.objectStoreNames.contains(DB_STORE_NAME)){
            db.createObjectStore(DB_STORE_NAME)
        }
    }
    request.onerror = {
        checkCompletable.complete(false)
    }
    request.onsuccess = { event: dynamic ->
        val db = event.target.result
        val store = db.transaction(DB_STORE_NAME, "readwrite").objectStore(DB_STORE_NAME).get(DB_STORE_KEY)
        store.onsuccess = { data: dynamic ->
            checkCompletable.complete(data.target.result != null)
        }
        store.onerror = {
            checkCompletable.complete(false)
        }
    }
    return checkCompletable.await()
}

suspend fun loadDbFromIndexedDb(dbName: String, version: Int, worker: Worker): Boolean {
    val exportCompletable = CompletableDeferred<Boolean>()
    val request = indexedDb.open(dbName, version)

    worker.onmessage =  {it: dynamic ->
        val id = it.data["id"].toString().toInt()
        if(id == ACTION_DB_OPEN){
            exportCompletable.complete(true)
        }
    }
    request.onsuccess =  { event: dynamic ->
        val db = event.target.result
        val store = db.transaction(DB_STORE_NAME, "readwrite").objectStore(DB_STORE_NAME).get(DB_STORE_KEY)
        store.onsuccess = { data: dynamic ->
            val option = json(Pair("id",ACTION_DB_OPEN))
            option["action"] = "open"
            option["buffer"] = data.target.result
            worker.postMessage(option)
        }
        store.onerror = {
            exportCompletable.complete(false)
        }
    }
    request.onerror = {
        exportCompletable.complete(false)
    }
    return exportCompletable.await()
}

suspend fun importDbToIndexedDb(dbName: String, version: Int, worker: Worker): Boolean {
    val exportCompletable = CompletableDeferred<Boolean>()
    worker.onmessage =  {it: dynamic ->
        val id = it.data["id"].toString().toInt()
        if(id == 12){
            val request = indexedDb.open(dbName, version)
            request.onsuccess = { event: dynamic ->
                val db = event.target.result
                val transaction = db.transaction(DB_STORE_NAME, "readwrite")
                transaction.oncomplete = {
                    exportCompletable.complete(true)
                }
                transaction.onerror = {
                    exportCompletable.complete(false)
                }
                val store = transaction.objectStore(DB_STORE_NAME)
                store.put(it.data.buffer,DB_STORE_KEY)
            }
        }
    }
    val option = json(Pair("id",ACTION_SQL_EXPORT))
    option["action"] = "export"
    worker.postMessage(option)
    return exportCompletable.await()
}