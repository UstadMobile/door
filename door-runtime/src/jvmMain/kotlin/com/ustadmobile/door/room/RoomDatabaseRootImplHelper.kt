package com.ustadmobile.door.room

import com.ustadmobile.door.DoorRootDatabase
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.ext.rootDatabase
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.util.InvalidationTrackerTransactionListener
import com.ustadmobile.door.util.TransactionMode
import com.ustadmobile.door.util.systemTimeInMillis
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

actual class RoomDatabaseRootImplHelper actual constructor(
    dataSource: DataSource,
    db: RoomDatabase,
    tableNames: List<String>,
    invalidationTracker: InvalidationTracker,
    dbType: Int,
    closeDataSourceOnClose: Boolean,
) : RoomDatabaseRootImplHelperCommon(dataSource, db, tableNames, invalidationTracker, dbType, closeDataSourceOnClose) {

    inner class PendingTransaction(val connection: Connection)

    private val pendingTransactionThreadMap = concurrentSafeMapOf<Long, PendingTransaction>()

    @Suppress("UNUSED_PARAMETER") //Reserved for future usage
    private fun <R> useNewConnectionInternal(
        transactionMode: TransactionMode,
        block: (Connection) -> R,
        threadId: Long,
    ): R {
        val startTime = systemTimeInMillis()
        val transaction = PendingTransaction(dataSource.connection)
        transaction.connection.autoCommit = false
        pendingTransactionThreadMap[threadId] = transaction
        val invalidationTrackerListener = invalidationTracker as? InvalidationTrackerTransactionListener
        return try {
            invalidationTrackerListener?.beforeTransactionBlock(transaction.connection)

            block(transaction.connection).also {
                invalidationTrackerListener?.afterTransactionBlock(transaction.connection)

                transaction.connection.commit()

                invalidationTrackerListener?.afterTransactionCommitted(transaction.connection)
            }
        }catch(e: Exception) {
            Napier.e("useConnection: ERROR", e)
            if(!transaction.connection.autoCommit) {
                transaction.connection.rollback()
            }

            throw e
        }finally {
            pendingTransactionThreadMap.remove(threadId)
            transaction.connection.close()
            if(pendingTransactionThreadMap.isNotEmpty()) {
                Napier.d("useConnection: close connection for thread #$threadId (took ${systemTimeInMillis() - startTime}ms)" +
                        " There are ${pendingTransactionThreadMap.size} pending non-async transactions still open.")
            }
        }
    }

    actual fun <R> useConnection(
        transactionMode: TransactionMode,
        block: (Connection) -> R,
    ): R {
        val threadId = Thread.currentThread().id
        val threadPendingTransaction = pendingTransactionThreadMap[threadId]
        val dbQueryTimeoutMs = ((db.rootDatabase as DoorRootDatabase).jdbcQueryTimeout * 1000).toLong()

        return if(threadPendingTransaction != null) {
            try {
                block(threadPendingTransaction.connection)
            }catch(e: Exception){
                Napier.e("useConnection: Exception!", e)
                throw e
            }
        }else if(dbType == DoorDbType.SQLITE) {
            runBlocking {
                withTimeout(dbQueryTimeoutMs) {
                    sqliteMutex.withLock {
                        useNewConnectionInternal(transactionMode, block, threadId)
                    }
                }
            }
        }else {
            useNewConnectionInternal(transactionMode, block, threadId)
        }
    }

    actual fun <R> useConnection(block: (Connection) -> R) = useConnection(TransactionMode.READ_WRITE, block)

    override fun close() {
        pendingTransactionThreadMap.forEach {
            it.value.connection.close()
        }
        super.close()
    }

}