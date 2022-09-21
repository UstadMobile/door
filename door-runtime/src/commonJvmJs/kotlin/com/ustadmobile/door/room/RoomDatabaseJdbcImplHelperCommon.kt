package com.ustadmobile.door.room

import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.ext.rootDatabase
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.ext.mutableLinkedListOf
import com.ustadmobile.door.util.TransactionMode
import com.ustadmobile.door.util.systemTimeInMillis
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

abstract class RoomDatabaseJdbcImplHelperCommon(
    protected val dataSource: DataSource,
    protected val db: RoomDatabase,
    private val tableNames: List<String>,
    val invalidationTracker: InvalidationTracker,
    val dbType: Int = DoorDbType.SQLITE
) {

    private val transactionIdAtomic = atomic(0)

    private val openTransactions = concurrentSafeMapOf<Int, TransactionElement>()

    protected val sqliteMutex = Mutex()

    class TransactionElement(
        override val key: Key,
        val connection: Connection,
        val transactionId: Int = 0,
    ) : CoroutineContext.Element

    /**
     * Setup triggers (if needed) for SQLite change tracking. On JVM (where multiple connections can operate run at
     * the same time
     */
    abstract suspend fun Connection.setupSqliteTriggersAsync()

    @Suppress("UNUSED_PARAMETER") //Reserved for future use
    private suspend fun <R> useNewConnectionAsyncInternal(
        transactionMode: TransactionMode,
        block: suspend (Connection) -> R,
    ): R {

        val connection = dataSource.getConnection()
        connection.setAutoCommit(false)

        val transactionId = transactionIdAtomic.incrementAndGet()
        val transactionStartTime = systemTimeInMillis()
        val changedTables = mutableLinkedListOf<String>()

        return try {
            if(dbType == DoorDbType.SQLITE) {
                connection.setupSqliteTriggersAsync()
            }

            val transactionElement = TransactionElement(Key, connection, transactionId)
            openTransactions[transactionId] = transactionElement
            val result = withContext(transactionElement) {
                block(transactionElement.connection)
            }

            if(dbType == DoorDbType.SQLITE) {
                changedTables.addAll(invalidationTracker.findChangedTablesOnConnectionAsync(connection))
            }

            connection.commit()

            invalidationTracker.takeIf { changedTables.isNotEmpty() }?.onTablesInvalidated(changedTables.toSet())

            result
        }catch(t: Throwable) {
            Napier.e("useConnectionAsync: transaction ERROR: useConnectionAsync (transaction #${transactionId}: " +
                    "Transactions [${openTransactions.keys.joinToString()}] are still open" +
                    "Exception", t)
            if(!connection.getAutoCommit())
                connection.rollback()

            throw t
        }finally {
            connection.close()
            openTransactions.remove(transactionId)
            if(openTransactions.isNotEmpty())
                Napier.w("useConnectionAsync: close transaction $transactionId (took ${systemTimeInMillis() - transactionStartTime}ms)." +
                    "There are Transactions [${openTransactions.keys.joinToString()}] pending async transactions still open.")
        }
    }

    @Suppress("UNUSED_PARAMETER") //Reserved for future usage
    suspend fun <R> useConnectionAsync(
        transactionMode: TransactionMode,
        block: suspend (Connection) -> R
    ): R {
        val transactionContext = coroutineContext[Key]
        val dbQueryTimeoutMs = ((db.rootDatabase as DoorDatabaseJdbc).jdbcQueryTimeout * 1000).toLong()

        return if(transactionContext != null) {
            //continue using existing connection for the transaction
            withContext(transactionContext) {
                block(transactionContext.connection)
            }
        }else if(dbType == DoorDbType.SQLITE){
            withTimeout(dbQueryTimeoutMs) {
                sqliteMutex.withLock {
                    useNewConnectionAsyncInternal(transactionMode, block)
                }
            }
        }else {
            withTimeout(dbQueryTimeoutMs) {
                useNewConnectionAsyncInternal(transactionMode, block)
            }
        }
    }

    suspend fun <R> useConnectionAsync(
        block: suspend (Connection) -> R
    ) = useConnectionAsync(TransactionMode.READ_WRITE, block)

    companion object Key : CoroutineContext.Key<TransactionElement>


}