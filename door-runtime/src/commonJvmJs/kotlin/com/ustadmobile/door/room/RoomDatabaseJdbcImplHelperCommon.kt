package com.ustadmobile.door.room

import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.ext.rootDatabase
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ConnectionAsync
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.ext.mutableLinkedListOf
import com.ustadmobile.door.log.*
import com.ustadmobile.door.util.systemTimeInMillis
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

abstract class RoomDatabaseJdbcImplHelperCommon(
    protected val dataSource: DataSource,
    protected val db: RoomDatabase,
    val dbName: String,
    val logger: DoorLogger,
    private val tableNames: List<String>,
    val invalidationTracker: InvalidationTracker,
    val dbType: Int = DoorDbType.SQLITE
) {

    private val transactionIdAtomic = atomic(0)

    private val openTransactions = concurrentSafeMapOf<Int, TransactionElement>()

    protected val sqliteMutex = Mutex()

    private val listeners = concurrentSafeListOf<Listener>()

    @Volatile
    private var closed = atomic(false)

    protected val logPrefix: String = "[RoomJdbcImplHelper - $dbName]"

    internal interface Listener {
        suspend fun onBeforeTransactionAsync(
            readOnly: Boolean,
            connection: Connection,
            transactionId: Int,
        )

        suspend fun onAfterTransactionAsync(
            readOnly: Boolean,
            connection: Connection,
            transactionId: Int,
        )

        suspend fun onTransactionCommittedAsync(
            readOnly: Boolean,
            connection: Connection,
            transactionId: Int,
        )

        fun onBeforeTransaction(
            readOnly: Boolean,
            connection: Connection,
            transactionId: Int,
        )

        fun onAfterTransaction(
            readOnly: Boolean,
            connection: Connection,
            transactionId: Int,
        )

    }

    class TransactionElement(
        override val key: Key,
        val connection: Connection,
        @Suppress("unused") //Reserved for future use and debugging purposes
        val transactionId: Int = 0,
        val readOnly: Boolean,
    ) : CoroutineContext.Element

    /**
     * Setup triggers (if needed) for SQLite change tracking. On JVM (where multiple connections can operate run at
     * the same time
     */
    abstract suspend fun Connection.setupSqliteTriggersAsync()

    protected fun assertNotClosed() {
        if(closed.value)
            throw IllegalStateException("$this is closed!")
    }

    private suspend fun <R> useNewConnectionAsyncInternal(
        readOnly: Boolean,
        block: suspend (Connection) -> R,
    ): R {
        assertNotClosed()

        val connection = dataSource.getConnection()
        val connectionId = transactionIdAtomic.incrementAndGet()

        val connectionLogPrefix = "$logPrefix - useNewConnectionAsyncInternal connection #$connectionId "
        logger.v { "$connectionLogPrefix - start readOnly=$readOnly" }

        if(!readOnly) {
            if(connection is ConnectionAsync) {
                connection.setAutoCommitAsync(false)
            }else {
                connection.setAutoCommit(false)
            }
        }


        val transactionStartTime = systemTimeInMillis()
        val changedTables = mutableLinkedListOf<String>()

        return try {
            if(!readOnly && dbType == DoorDbType.SQLITE) {
                logger.v { "$connectionLogPrefix: creating SQLite change triggers "}
                connection.setupSqliteTriggersAsync()
            }

            val transactionElement = TransactionElement(Key, connection, connectionId, readOnly)
            openTransactions[connectionId] = transactionElement
            listeners.forEach {
                it.onBeforeTransactionAsync(readOnly, connection, connectionId)
            }

            val result = withContext(transactionElement) {
                block(transactionElement.connection)
            }

            if(!readOnly && dbType == DoorDbType.SQLITE) {
                val changes = invalidationTracker.findChangedTablesOnConnectionAsync(connection)
                logger.d { "$connectionLogPrefix: SQLite Change Tracker: Changed tables=[${changes.joinToString()}]" }
                changedTables.addAll(changes)
            }

            listeners.forEach {
                it.onAfterTransactionAsync(readOnly, connection, connectionId)
            }

            if(!readOnly) {
                if(connection is ConnectionAsync)
                    connection.commitAsync()
                else
                    connection.commit()
                logger.v { "$connectionLogPrefix committed changes" }
            }

            invalidationTracker.takeIf { changedTables.isNotEmpty() }?.onTablesInvalidated(changedTables.toSet())
            listeners.forEach {
                it.onTransactionCommittedAsync(readOnly, connection, connectionId)
            }

            result
        }catch(t: Throwable) {
            withContext(NonCancellable) {
                if(t is CancellationException) {
                    logger.v { "$connectionLogPrefix : cancelled ${t.message}"}
                }else {
                    logger.e("$connectionLogPrefix exception ", t)
                }

                if(!connection.isClosed() && !readOnly) {
                    logger.i(message = "$connectionLogPrefix Attempting to rollback transaction #${connectionId}")
                    if(connection is ConnectionAsync)
                        connection.rollbackAsync()
                    else
                        connection.rollback()
                    logger.i(message = "$connectionLogPrefix Rolled back changes")
                }
            }

            throw t
        }finally {
            connection.close()
            openTransactions.remove(connectionId)
            logger.v { "$connectionLogPrefix: end transaction #$connectionId" }

            if(openTransactions.isNotEmpty())
                logger.w("useConnectionAsync: close transaction $connectionId (took ${systemTimeInMillis() - transactionStartTime}ms)." +
                    "There are Transactions [${openTransactions.keys.joinToString()}] pending async transactions still open.")
        }
    }

    /**
     * Use a Connection. If there is already a connection associated with the coroutine context
     * (from withDoorTransactionAsync), then that connection / context will be used, otherwise a new connection context
     * will be created
     *
     * @param readOnly true if only non-modifying (e.g. select queries) will be run using this connection. This helps
     *        improve performance : setting up change catch triggers can be skipped, look for changed tables can be
     *        skipped, and on servers, this could allow the use of read-only replicas.
     */
    suspend fun <R> useConnectionAsync(
        readOnly: Boolean,
        block: suspend (Connection) -> R
    ): R {
        val transactionContext = coroutineContext[Key]
        val dbQueryTimeoutMs = ((db.rootDatabase as DoorDatabaseJdbc).jdbcQueryTimeout * 1000).toLong()

        return if(transactionContext != null) {
            //continue using existing connection for the transaction
            if(transactionContext.readOnly && !readOnly) {
                //If the current context is readOnly, and a read-write connection is requested, that is not allowed.
                throw IllegalStateException("Current transaction context is read-only")
            }

            withContext(transactionContext) {
                block(transactionContext.connection)
            }
        }else if(dbType == DoorDbType.SQLITE){
            withTimeout(dbQueryTimeoutMs) {
                sqliteMutex.withLock {
                    useNewConnectionAsyncInternal(readOnly, block)
                }
            }
        }else {
            withTimeout(dbQueryTimeoutMs) {
                useNewConnectionAsyncInternal(readOnly, block)
            }
        }
    }

    suspend fun <R> useConnectionAsync(
        block: suspend (Connection) -> R
    ) = useConnectionAsync(false, block)

    companion object Key : CoroutineContext.Key<TransactionElement>


    internal fun addListener(listener: Listener) {
        listeners += listener
    }

    internal fun removeListener(listener: Listener) {
        listeners -= listener
    }

    /**
     * This function is called by the DatabaseBuilder after table creation, migration, and callbacks have been run. It
     * is currently used to initialize postgres change tracking triggers.
     */
    internal open fun onStartChangeTracking() {

    }

    fun close() {
        //close connections for any open transactions
        if(!closed.getAndSet(true)) {
            openTransactions.forEach {
                try {
                    it.value.connection.close()
                }catch(e: Exception) {
                    logger.w("$logPrefix : exception closing connection for transaction #${it.key}")
                }
            }
            openTransactions.clear()
            onClose()
        }
    }

    /**
     * Can be overriden by child classes to implement additional logic required when closing.
     */
    protected open fun onClose() {

    }

}