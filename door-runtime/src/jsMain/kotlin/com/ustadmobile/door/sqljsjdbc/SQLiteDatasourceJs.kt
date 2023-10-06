package com.ustadmobile.door.sqljsjdbc
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.log.DoorLogger
import com.ustadmobile.door.log.d
import com.ustadmobile.door.log.i
import com.ustadmobile.door.log.v
import com.ustadmobile.door.sqljsjdbc.IndexedDb.DATABASE_VERSION
import com.ustadmobile.door.sqljsjdbc.IndexedDb.DB_STORE_KEY
import com.ustadmobile.door.sqljsjdbc.IndexedDb.DB_STORE_NAME
import com.ustadmobile.door.sqljsjdbc.IndexedDb.indexedDb
import js.typedarrays.Uint8Array
import kotlinx.browser.document
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.Worker
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import kotlin.js.Json
import kotlin.js.json

/**
 * Class responsible to manage all SQLite worker tasks
 *
 * @param logger The main DoorLogger
 * @param logWorkerMessages if true, will log every WebWorker message sent and received. VERY VERBOSE.
 *
 */
class SQLiteDatasourceJs(
    private val dbName: String,
    private val worker: Worker,
    private val logger: DoorLogger,
    private val logWorkerMessages: Boolean = false,
) : DataSource {

    private val pendingMessages = mutableMapOf<Int, CompletableDeferred<WorkerResult>>()

    private val executedSqlQueries = mutableMapOf<Int, String>()

    /**
     * SQLite.JS webworker is a one-at-a-time operation. Locking is implemented as follows:
     *
     *  When a transaction begins: the SQLiteConnectionJs.setAutoCommitAsync will call acquireLock. When the lock is
     *  acquired the owner is set to the connection. SendQuery/SendUpdate will send queries for the lock owner without
     *  waiting. Once the lock is acuiqred, the connection will send BEGIN TRANSACTION, COMMIT, and ROLLBACK as needed.
     *
     *  When there is no active transaction: the transaction mutex will be used with the owner set to the datasource.
     *
     */
    private val transactionMutex = Mutex()

    private val logPrefix: String = "[SQLiteDataSourceJs - $dbName]"

    private var closed = false

    private val scope = CoroutineScope(Dispatchers.Default + Job())

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

    private fun assertNotClosed() {
        if(closed)
            throw IllegalStateException("SQLiteDataSourceJs is closed!")
    }



    /**
     * Execute SQL task by sending a message via Worker
     * @param message message to be sent for SQLJs to execute
     */
    private suspend fun sendMessage(message: Json): WorkerResult {
        assertNotClosed()

        val completable = CompletableDeferred<WorkerResult>()
        val actionId = ++idCounter
        logger.takeIf { logWorkerMessages }?.v {
            "$logPrefix sendMessage #$actionId - sending action=${message["action"]} \n"
        }
        pendingMessages[actionId] = completable
        executedSqlQueries[actionId] = message["sql"].toString()
        message["id"] = actionId
        worker.postMessage(message)
        val result = completable.await()
        logger.takeIf { logWorkerMessages }?.v {"$logPrefix sendMessage #$actionId - got result \n" }
        return result

    }

    private fun makeMessage(sql: String, params: Array<Any?>? = arrayOf()): Json {
        return json(
            "action" to "exec",
            "sql" to sql,
            "params" to params,
            "config" to json("useBigInt" to true)
        )
    }

    /**
     *
     */
    private suspend fun <R> withTransactionLock(
        connection: Connection,
        block: suspend () -> R,
    ): R {
        //this connection already holds the lock, so just run the block
        return if (transactionMutex.holdsLock(connection)) {
            block()
        }else {
            transactionMutex.withLock(owner = connection) {
                block()
            }
        }
    }

    internal suspend fun acquireTransactionLock(connection: Connection)  {
        transactionMutex.lock(owner = connection)
    }

    internal fun releaseTransactionLock(connection: Connection) {
        transactionMutex.unlock(owner = connection)
    }


    internal suspend fun sendQuery(
        connection: Connection,
        sql: String,
        params: Array<Any?>? = null
    ): ResultSet = withTransactionLock(connection) {
        logger.takeIf { logWorkerMessages }?.v { "$logPrefix sending query: $sql params=${params?.joinToString()}" }
        val results = sendMessage(makeMessage(sql, params)).results
        val sqliteResultSet = results?.let { SQLiteResultSet(it) } ?: SQLiteResultSet(arrayOf())
        logger.takeIf { logWorkerMessages }?.v {
            "$logPrefix Got result: Ran: '$sql' params=${params?.joinToString()} result = $sqliteResultSet\n"
        }
        sqliteResultSet
    }

    internal suspend fun sendUpdate(
        connection: Connection,
        sql: String,
        params: Array<Any?>?,
        returnGeneratedKey: Boolean = false
    ): UpdateResult = withTransactionLock(connection) {
        logger.takeIf { logWorkerMessages }?.v {
            "$logPrefix sending update: '$sql', params=${params?.joinToString()}\n"
        }
        sendMessage(makeMessage(sql, params))
        val generatedKey = if(returnGeneratedKey) {
            sendMessage(makeMessage("SELECT last_insert_rowid()")).results?.let { SQLiteResultSet(it) }
        }else {
            null
        }
        logger.takeIf { logWorkerMessages }?.v {"$logPrefix update done: '$sql'" }
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
    @Suppress("unused") // used in js code
    suspend fun exportDatabaseToFile() {
        transactionMutex.withLock {
            val result = sendMessage(json("action" to "export"))
            val typedArray: Uint8Array = result.buffer ?: throw IllegalStateException("no result buffer")
            val blob = Blob(arrayOf(typedArray.buffer))
            val link = document.createElement("a") as HTMLAnchorElement
            document.body?.appendChild(link)
            link.href = URL.createObjectURL(blob)
            link.download = "$dbName.db"
            link.click()
        }
    }

    /**
     * Save SQL.JS database to the indexed Database
     */
    suspend fun saveDatabaseToIndexedDb(): Boolean {
        val exportCompletable = CompletableDeferred<Boolean>()

        return transactionMutex.withLock(owner = this) {
            logger.d { "$logPrefix SQLiteDataSource/JS: saving to indexed db" }
            val result = sendMessage(json("action" to "export"))
            val request = indexedDb.open(dbName, DATABASE_VERSION)

            request.onsuccess = { event: dynamic ->
                val db = event.target.result
                val transaction = db.transaction(DB_STORE_NAME, "readwrite")
                transaction.oncomplete = {
                    logger.i("$logPrefix Saved to IndexedDb: $dbName")
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

            exportCompletable.await().also {
                logger.d("$logPrefix SQLiteDataSource/JS: saving to indexed db complete")
            }

        }
    }


    override fun getConnection(): Connection {
        assertNotClosed()
        return SQLiteConnectionJs(this)
    }

    fun close() {
        logger.d("$logPrefix close - terminating worker\n")
        worker.terminate()
        scope.cancel()
        closed = true
        logger.i("$logPrefix close - worker terminated, closed\n")
    }


    companion object {
        var idCounter = 0

        val PROTOCOL_SQLITE_PREFIX = "sqlite:"

        const val LOCATION_MEMORY = ":memory:"


    }
}