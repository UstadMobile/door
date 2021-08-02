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

    var generatedKeys: ResultSet = SQLiteResultSet(arrayOf())

    init {
        worker.onmessage = { dbEvent: dynamic ->
            val actionId = dbEvent.data["id"].toString().toInt()
            val executedQuery = executedSqlQueries[actionId]
            if(dbEvent.data["error"] != js("undefined")){
                throw SQLException(dbEvent.data["error"].toString(),
                    Exception("Error occurred when executing $executedQuery"))
            }

            val pendingCompletable = pendingMessages.remove(actionId)
            if(pendingCompletable != null){

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
    suspend fun sendMessage(message: Json): WorkerResult {
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
            "params" to JSON.parse(
                JSON.stringify(params?.map { (if(it != js("undefined")) it else null).toString()
            }))
        )
    }

    internal suspend fun sendQuery(sql: String, params: Array<Any?>? = null): ResultSet {
        return sendMessage(makeMessage(sql, params)).results?.let { SQLiteResultSet(it) } ?: SQLiteResultSet(arrayOf())
    }

    internal suspend fun sendUpdate(sql: String, params: Array<Any?>?, returnGeneratedKey: Boolean = false): Int {
        val newSql = makeSqlQuery(sql,params, returnGeneratedKey)
        val workerResult = sendMessage(makeMessage(newSql, if(returnGeneratedKey) arrayOf() else params))
        val results = workerResult.results
        if(results != null){
            generatedKeys = SQLiteResultSet(results)
        }
        return workerResult.let { if(it.ready) 1 else 0 }
    }


    /**
     * SQL JS doesn't work when you try to execute multiple queries at a time with one of them having params.
     * It will try to bind params event to that query with no params which will result to column
     * index out of range exception.
     *
     * Instead, we generate new SQL query with inline values so that we can execute those queries without passing
     * any params.
     *
     * NOTE:
     * SQL inline values is known for slowing down SQL performance.
     */
    private fun makeSqlQuery(sql: String, params: Array<Any?>?, returnGeneratedKey: Boolean): String {
        if(!returnGeneratedKey){
            return sql
        }else {
            var paramTracker = -1
            return sql.map{
                val newChar = if(it.toString() == "?") {
                    paramTracker += 1
                    val param = params?.get(paramTracker)
                    val isNotAString = param != null && (param !is String
                            || "$param".matches("-?\\d+(\\.\\d+)?".toRegex()))
                    val newParam = when {
                        isNotAString -> param
                        else -> "'$param'"
                    }
                    newParam.toString()
                }else{
                    it
                }
                newChar
            }.joinToString("")+ ";SELECT last_insert_rowid();"
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