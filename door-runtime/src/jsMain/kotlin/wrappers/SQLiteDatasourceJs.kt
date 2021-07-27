package wrappers
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.SQLException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.Worker
import wrappers.IndexedDb.DATABASE_VERSION
import wrappers.IndexedDb.DB_STORE_KEY
import wrappers.IndexedDb.DB_STORE_NAME
import wrappers.IndexedDb.indexedDb
import kotlin.js.Json
import kotlin.js.json

/**
 * Class responsible to manage all SQLite worker tasks
 */
class SQLiteDatasourceJs(private val dbName: String, private val worker: Worker) : DataSource{

    private val pendingMessages = mutableMapOf<Int, CompletableDeferred<WorkerResult>>()

    private val executedSqlQueries = mutableMapOf<Int, String>()

    init {
        worker.onmessage = { it: dynamic ->
            val actionId = it.data["id"].toString().toInt()
            val executedQuery = executedSqlQueries[actionId]
            if(it.data["error"] != js("undefined")){
                throw SQLException("Error occurred while trying to execute a query",
                    Exception("${it.data["error"]} when executing $executedQuery"))
            }
            val pendingCompletable = pendingMessages.remove(actionId)
            if(pendingCompletable != null){
                val executedSuccessfully = it.data["ready"] == (js("undefined") && it.data["results"] != js("undefined"))
                        || it.data["ready"]

                val results = if(it.data["results"] != js("undefined")) it.data["results"] else arrayOf<Any>()

                pendingCompletable.complete(
                    WorkerResult(it.data["id"], results, executedSuccessfully, it.data["buffer"])
                )
            }
        }
    }

    /**
     * Execute SQL task by sending a message via Worker
     * @param message message to be sent for SQLJs to execute
     */
    suspend fun sendMessage(message: Json): WorkerResult {
        val completable = CompletableDeferred<WorkerResult>()
        val actionId = ++idCounter
        pendingMessages[actionId] = completable
        executedSqlQueries[actionId] = message["sql"].toString()
        message["id"] = actionId
        worker.postMessage(message)
        return completable.await()
    }

    private fun makeMessage(sql: String, params: Array<Any?>?) : Json {
        val message = json(
            "action" to "exec",
            "sql" to sql
        )

        if(params != null) {
            message["params"] = params
        }
        return message
    }

    internal suspend fun sendQuery(sql: String, params: Array<Any?>?): ResultSet {
        return sendMessage(makeMessage(sql, params)).results?.let { SQLiteResultSet(it) } ?: SQLiteResultSet(arrayOf())
    }

    internal suspend fun sendUpdate(sql: String, params: Array<Any?>?): Int {
        return sendMessage(makeMessage(sql, params)).let { if(it.ready) 1 else 0 }
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
                Throwable("Error when importing database from IndexedDb to SQLite DB"))
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


    override fun getConnection(): Connection {
        return SQLiteConnectionJs(this)
    }


    companion object {
        var idCounter = 0
    }
}