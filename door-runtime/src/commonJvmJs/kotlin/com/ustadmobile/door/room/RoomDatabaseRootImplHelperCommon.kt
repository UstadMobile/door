package com.ustadmobile.door.room

import com.ustadmobile.door.DoorRootDatabase
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.jdbc.*
import com.ustadmobile.door.util.InvalidationTrackerTransactionListener
import com.ustadmobile.door.util.TransactionMode
import com.ustadmobile.door.util.systemTimeInMillis
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

abstract class RoomDatabaseRootImplHelperCommon(
    protected val dataSource: DataSource,
    protected val db: RoomDatabase,
    private val tableNames: List<String>,
    val invalidationTracker: InvalidationTracker,
    val dbType: Int,
    /**
     * If the DataSource was created just for this database (e.g. when we use a JDBC URL or on Javascript). This is false
     * when using a JNDI datasource (e.g. one that is managed by a J2EE container such as Tomcat etc).
     */
    private val closeDataSourceOnClose: Boolean
) {

    private val transactionIdAtomic = atomic(0)

    private val openTransactions = concurrentSafeMapOf<Int, TransactionElement>()

    protected val sqliteMutex = Mutex()

    class TransactionElement(
        override val key: Key,
        val connection: Connection,
        val transactionId: Int = 0,
    ) : CoroutineContext.Element


    @Suppress("UNUSED_PARAMETER") //Reserved for future use
    private suspend fun <R> useNewConnectionAsyncInternal(
        transactionMode: TransactionMode,
        block: suspend (Connection) -> R,
    ): R {
        val connection = dataSource.getConnectionAsyncOrFallback()
        connection.setAutoCommitAsyncOrFallback(false)

        val transactionId = transactionIdAtomic.incrementAndGet()
        val transactionStartTime = systemTimeInMillis()
        val invalidationTrackerListener = invalidationTracker as? InvalidationTrackerTransactionListener

        return try {
            invalidationTrackerListener?.beforeAsyncTransasctionBlock(connection)
            val transactionElement = TransactionElement(Key, connection, transactionId)
            openTransactions[transactionId] = transactionElement
            val result = withContext(transactionElement) {
                block(transactionElement.connection)
            }

            invalidationTrackerListener?.afterAsyncTransactionBlock(connection)

            connection.commitAsyncOrFallback()

            invalidationTrackerListener?.afterTransactionCommittedAsync(connection)

            result
        }catch(t: Throwable) {
            Napier.e("useConnectionAsync: transaction ERROR: useConnectionAsync (transaction #${transactionId}: " +
                    "Transactions [${openTransactions.keys.joinToString()}] are still open" +
                    "Exception = $t", t)
            if(!connection.getAutoCommit())
                connection.rollback()

            throw t
        }finally {
            withContext(NonCancellable) {
                connection.closeAsyncOrFallback()

                openTransactions.remove(transactionId)
                if(openTransactions.isNotEmpty())
                    Napier.w("useConnectionAsync: close transaction $transactionId (took ${systemTimeInMillis() - transactionStartTime}ms)." +
                            "There are Transactions [${openTransactions.keys.joinToString()}] pending async transactions still open.")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER") //Reserved for future usage
    suspend fun <R> useConnectionAsync(
        transactionMode: TransactionMode,
        block: suspend (Connection) -> R
    ): R {
        val transactionContext = coroutineContext[Key]
        val dbQueryTimeoutMs = ((db.rootDatabase as DoorRootDatabase).jdbcQueryTimeout * 1000).toLong()

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

    open fun close() {
        openTransactions.values.forEach {
            try {
                it.connection.close()
            }catch(e: Exception) {
                Napier.w("WARNING: closing database: Exception attempting to close connection ${it.connection}")
            }
        }

        dataSource.takeIf { closeDataSourceOnClose }?.closeIfCloseable()
    }

    companion object Key : CoroutineContext.Key<TransactionElement>


}