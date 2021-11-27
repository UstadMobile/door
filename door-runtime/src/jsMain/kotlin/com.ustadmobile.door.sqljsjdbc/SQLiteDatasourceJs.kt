package com.ustadmobile.door.sqljsjdbc
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.sqljsjdbc.IndexedDb.DATABASE_VERSION
import com.ustadmobile.door.sqljsjdbc.IndexedDb.DB_STORE_KEY
import com.ustadmobile.door.sqljsjdbc.IndexedDb.DB_STORE_NAME
import com.ustadmobile.door.sqljsjdbc.IndexedDb.indexedDb
import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.Worker
import kotlin.js.Json
import kotlin.js.json

/**
 * Class responsible to manage all SQLite worker tasks
 */
class SQLiteDatasourceJs(private val dbName: String, private val worker: Worker) : DataSource{

    private val pendingMessages = mutableMapOf<Int, CompletableDeferred<WorkerResult>>()

    private val executedSqlQueries = mutableMapOf<Int, String>()

    private val sendUpdateLock = Mutex()

    init {
        worker.onmessage = { dbEvent: dynamic ->
            val actionId = dbEvent.data["id"].toString().toInt()
            val executedQuery = executedSqlQueries.remove(actionId)
            val pendingCompletable = pendingMessages.remove(actionId)
            if(pendingCompletable != null){

                if(dbEvent.data["error"] != js("undefined")){
                    val exception = SQLException(dbEvent.data["error"].toString(),
                        Exception("Error occurred when executing $executedQuery"))
                    pendingCompletable.completeExceptionally(exception)
                }

                val executedSuccessfully = dbEvent.data["ready"] == (js("undefined")
                        && dbEvent.data["results"] != js("undefined")) || dbEvent.data["ready"]

                val results = if(dbEvent.data["results"] != js("undefined")) dbEvent.data["results"] else arrayOf<Any>()
                val buffer = if(dbEvent.data["buffer"] != js("undefined")) dbEvent.data["buffer"] else null

                pendingCompletable.complete(WorkerResult(dbEvent.data["id"], results, executedSuccessfully, buffer))
            }
        }
    }

    /**
     * Execute SQL task by sending a message via Worker
     * @param message message to be sent for SQLJs to execute
     */
    private suspend fun sendMessage(message: Json): WorkerResult {
        val completable = CompletableDeferred<WorkerResult>()
        val actionId = ++idCounter
        pendingMessages[actionId] = completable
        executedSqlQueries[actionId] = message["sql"].toString()
        message["id"] = actionId
        worker.postMessage(message)
        return completable.await()
    }

    private fun makeMessage(sql: String, params: Array<Any?>? = arrayOf()): Json {
        return json(
            "action" to "exec",
            "sql" to sql,
            "params" to params,
            "config" to json("useBigInt" to true)
        )
    }

    internal suspend fun sendQuery(sql: String, params: Array<Any?>? = null): ResultSet {
        val results = sendMessage(makeMessage(sql, params)).results
        return results?.let { SQLiteResultSet(it) } ?: SQLiteResultSet(arrayOf())
    }

    internal suspend fun sendUpdate(sql: String, params: Array<Any?>?, returnGeneratedKey: Boolean = false): UpdateResult {
        sendUpdateLock.withLock {
            sendMessage(makeMessage(sql, params))
            val generatedKey = if(returnGeneratedKey) {
                sendMessage(makeMessage("SELECT last_insert_rowid()")).results?.let { SQLiteResultSet(it) }
            }else {
                null
            }
            return UpdateResult(1, generatedKey)
        }
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
     * Save SQL.JS database to a .db file
     */
    @Suppress("UNUSED_VARIABLE") // used in js code
    suspend fun exportDatabaseToFile() {
        val result = sendMessage(json("action" to "export"))
        val blob = js("new Blob([result.buffer]);")
        val link = document.createElement("a") as HTMLAnchorElement
        document.body?.appendChild(link)
        link.href = js("window.URL.createObjectURL(blob);")
        link.download = "$dbName.db"
        link.click()
    }

    /**
     * Save SQL.JS database to the indexed Database
     */
    suspend fun saveDatabaseToIndexedDb(): Boolean {
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