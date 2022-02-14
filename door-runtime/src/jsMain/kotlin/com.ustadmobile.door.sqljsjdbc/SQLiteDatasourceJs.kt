package com.ustadmobile.door.sqljsjdbc
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.sqljsjdbc.IndexedDb.DATABASE_VERSION
import com.ustadmobile.door.sqljsjdbc.IndexedDb.DB_STORE_KEY
import com.ustadmobile.door.sqljsjdbc.IndexedDb.DB_STORE_NAME
import com.ustadmobile.door.sqljsjdbc.IndexedDb.indexedDb
import com.ustadmobile.door.util.SqliteChangeTracker
import com.ustadmobile.door.util.TransactionMode
import io.github.aakira.napier.Napier
import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.Worker
import kotlin.coroutines.coroutineContext
import kotlin.js.Json
import kotlin.js.json

/**
 * Class responsible to manage all SQLite worker tasks
 */
class SQLiteDatasourceJs(
    private val dbName: String,
    private val worker: Worker
) : DataSource{

    private val pendingMessages = mutableMapOf<Int, CompletableDeferred<WorkerResult>>()

    private val executedSqlQueries = mutableMapOf<Int, String>()

    private val transactionMutex = Mutex()

    private val logPrefix: String = "SQLiteDataSourceJs [$dbName]"

    // This starts out false so we don't create problems when the database is being built (e.g. create tables etc).
    // The builder will set it to true once ready
    internal var changeTrackingEnabled: Boolean = false

    private var transactionIdCounter = 0

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

                val results = if(dbEvent.data["results"] != js("undefined"))
                    dbEvent.data["results"]
                else
                    arrayOf<Any>()

                val buffer = if(dbEvent.data["buffer"] != js("undefined"))
                    dbEvent.data["buffer"]
                else
                    null
                pendingCompletable.complete(WorkerResult(dbEvent.data["id"], results, executedSuccessfully, buffer))
            }
        }
    }

    // This is here because we need the mutex lock on datasource (there could be any number of connections). We need to
    internal suspend fun <R> withTransactionLock(
        transactionMode: TransactionMode = TransactionMode.READ_WRITE,
        block: suspend () -> R
    ) : R {
        with(transactionMutex) {
            val key = ReentrantMutexContextKey(this)
            // call block directly when this mutex is already locked in the context
            val reentrantContext = coroutineContext[key]
            if (reentrantContext != null) {
                if(transactionMode == TransactionMode.READ_WRITE && reentrantContext.key.readOnly)
                    throw SQLException("Starting a read/write transaction nested with a read only transaction is not" +
                            "allowed!")

                return block()
            }

            // otherwise add it to the context and lock the mutex
            return withContext(ReentrantMutexContextElement(key)) {
                withLock {
                    val transactionId = ++transactionIdCounter
                    Napier.i("Transaction: Start Transaction $transactionId", tag = DoorTag.LOG_TAG)
                    var transactionSuccessful = false
                    try {
                        if(transactionMode == TransactionMode.READ_WRITE)
                            sendUpdate("BEGIN TRANSACTION", emptyArray())

                        block().also {
                            if(transactionMode == TransactionMode.READ_WRITE) {
                                sendUpdate("COMMIT", emptyArray() )
                            }

                            transactionSuccessful = true
                        }
                    }catch(e: Exception) {
                        Napier.e("withTransactionLock: Exception! ", e, tag = DoorTag.LOG_TAG)
                        throw e
                    }finally {
                        Napier.i("Transaction: End transaction $transactionId", tag = DoorTag.LOG_TAG)
                        if(!transactionSuccessful && transactionMode == TransactionMode.READ_WRITE)
                            sendUpdate("ROLLBACK", emptyArray())
                    }
                }
            }
        }
    }

    /**
     * Execute SQL task by sending a message via Worker
     * @param message message to be sent for SQLJs to execute
     */
    private suspend fun sendMessage(message: Json): WorkerResult {
        return transactionMutex.withReentrantLock {
            val completable = CompletableDeferred<WorkerResult>()
            val actionId = ++idCounter
            Napier.d("$logPrefix sendMessage #$actionId - sending action=${message["action"]}", tag = DoorTag.LOG_TAG)
            pendingMessages[actionId] = completable
            executedSqlQueries[actionId] = message["sql"].toString()
            message["id"] = actionId
            worker.postMessage(message)
            val result = completable.await()
            Napier.d("$logPrefix sendMessage #$actionId - got result", tag = DoorTag.LOG_TAG)
            result
        }
    }

    private fun makeMessage(sql: String, params: Array<Any?>? = arrayOf()): Json {
        return json(
            "action" to "exec",
            "sql" to sql,
            "params" to params,
            "config" to json("useBigInt" to true)
        )
    }

    internal suspend fun sendQuery(
        sql: String,
        params: Array<Any?>? = null
    ): ResultSet = withTransactionLock(transactionMode = TransactionMode.READ_ONLY) {
        Napier.d("$logPrefix sending query: $sql params=${params?.joinToString()}", tag = DoorTag.LOG_TAG)
        val results = sendMessage(makeMessage(sql, params)).results
        val sqliteResultSet = results?.let { SQLiteResultSet(it) } ?: SQLiteResultSet(arrayOf())
        Napier.d("$logPrefix Got result: Ran: '$sql' params=${params?.joinToString()} result = $sqliteResultSet", tag = DoorTag.LOG_TAG)
        sqliteResultSet
    }

    internal suspend fun sendUpdate(
        sql: String,
        params: Array<Any?>?,
        returnGeneratedKey: Boolean = false
    ): UpdateResult = withTransactionLock{
        Napier.d("$logPrefix sending update: '$sql', params=${params?.joinToString()}",
            tag = DoorTag.LOG_TAG)
        sendMessage(makeMessage(sql, params))
        val generatedKey = if(returnGeneratedKey) {
            sendMessage(makeMessage("SELECT last_insert_rowid()")).results?.let { SQLiteResultSet(it) }
        }else {
            null
        }
        Napier.d("$logPrefix update done: '$sql'", tag = DoorTag.LOG_TAG)
        UpdateResult(1, generatedKey)
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
        transactionMutex.withLock {
            val result = sendMessage(json("action" to "export"))
            val blob = js("new Blob([result.buffer]);")
            val link = document.createElement("a") as HTMLAnchorElement
            document.body?.appendChild(link)
            link.href = js("window.URL.createObjectURL(blob);")
            link.download = "$dbName.db"
            link.click()
        }
    }

    /**
     * Save SQL.JS database to the indexed Database
     */
    suspend fun saveDatabaseToIndexedDb(): Boolean {
        val exportCompletable = CompletableDeferred<Boolean>()
        val result = sendMessage(json("action" to "export"))

        return transactionMutex.withLock {
            val request = indexedDb.open(dbName, DATABASE_VERSION)

            request.onsuccess = { event: dynamic ->
                val db = event.target.result
                val transaction = db.transaction(DB_STORE_NAME, "readwrite")
                transaction.oncomplete = {
                    Napier.i("Saved to IndexedDb: $dbName", tag = DoorTag.LOG_TAG)
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
            exportCompletable.await()
        }
    }


    /**
     * Find tables that have been changed using the SQLite triggers setup by SQLiteChangeTracker.
     */
    internal suspend fun findUpdatedTables(dbMetadata: DoorDatabaseMetadata<*>): List<String> {
        val changedTables = sendQuery(SqliteChangeTracker.FIND_CHANGED_TABLES_SQL).useResults { results ->
            results.mapRows {
                dbMetadata.allTables[it.getInt(1)]
            }
        }

        sendUpdate(SqliteChangeTracker.RESET_CHANGED_TABLES_SQL, emptyArray())

        return changedTables
    }

    override fun getConnection(): Connection {
        return SQLiteConnectionJs(this)
    }


    companion object {
        var idCounter = 0
    }
}