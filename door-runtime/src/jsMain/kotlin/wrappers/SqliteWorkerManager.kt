package wrappers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.Worker
import wrappers.IndexedDb.DATABASE_VERSION
import kotlin.js.Json
import kotlin.js.json

/**
 * Class responsible to manage all SQLite worker tasks
 */
class SqliteWorkerManager(private val dbName: String,private val worker: Worker) {

    /**
     * Execute SQL task by sending a message via Worker
     * @param message message to be sent for SQLJs to execute
     */
    suspend fun sendMessage(message: Json): WorkerResult {
        worker.onmessage = { it: dynamic ->
            val pendingCompletable = pendingMessages[it.data["id"].toString().toInt()]
            if(pendingCompletable != null){
                pendingCompletable.complete(
                    WorkerResult(it.data["id"], it.data["results"], it.data["ready"], it.data["buffer"])
                )
            }
        }
        val completable = CompletableDeferred<WorkerResult>()
        val actionId = ++idCounter
        pendingMessages[actionId] = completable
        message["id"] = actionId
        worker.postMessage(message)
        return completable.await()
    }

    /**
     * Load a stored database from indexed database to SQLJs database
     */
    suspend fun loadDbFromIndexedDb(): Boolean {
        val exportCompletable = CompletableDeferred<Boolean>()
        val request = indexedDb.open(dbName, DATABASE_VERSION)
        request.onsuccess =  { event: dynamic ->
            val db = event.target.result
            val store = db.transaction(DB_STORE_NAME, "readwrite").objectStore(DB_STORE_NAME).get(DB_STORE_KEY)
            store.onsuccess = { data: dynamic ->
                GlobalScope.launch {
                    val result = sendMessage(json("action" to "open", "buffer" to data.target.result))
                    exportCompletable.complete(result.ready)
                }
            }
            store.onerror = {
                exportCompletable.completeExceptionally(
                    Throwable("Error when executing store data fetch"))
            }
        }
        request.onerror = {
            exportCompletable.completeExceptionally(
                Throwable("Error when imporing databsed from IndexedDb to SQLite DB"))
        }
        return exportCompletable.await()
    }

    /**
     * Import SQLJs database to the indexed Database
     */
    suspend fun importDbToIndexedDb(): Boolean {
        val exportCompletable = CompletableDeferred<Boolean>()
        val result = sendMessage(json("action" to "export"))
        val request = indexedDb.open(dbName, DATABASE_VERSION)
        request.onsuccess = { event: dynamic ->
            console.log("on succes")
            val db = event.target.result
            val transaction = db.transaction(DB_STORE_NAME, "readwrite")
            transaction.oncomplete = {
                exportCompletable.complete(true)
            }
            transaction.onerror = {
                exportCompletable.completeExceptionally(
                    Throwable("Error when importing SQLJs database to IndexedDb")
                )
            }
            val store = transaction.objectStore(DB_STORE_NAME)
            store.put(result.buffer, DB_STORE_KEY)
        }
        return exportCompletable.await()
    }





    companion object {

        val pendingMessages = mutableMapOf<Int, CompletableDeferred<WorkerResult>>()

        var idCounter = 0

        val indexedDb = js("window.indexedDB || window.mozIndexedDB || window.webkitIndexedDB || window.msIndexedDB")

        private const val DB_STORE_NAME = "um_db_store"

        private const val DB_STORE_KEY = "um_db_key"
    }
}