package com.ustadmobile.door

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.util.TransactionMode
import kotlinx.atomicfu.atomic

actual class RoomDatabaseJdbcImplHelper actual constructor(
    dataSource: DataSource
) : RoomDatabaseJdbcImplHelperCommon(dataSource) {

    inner class PendingTransaction(
        val connection: Connection
    ) {
        val depth = atomic(0)
    }

    //Switch this to using concurrentSafeMap
    //Synchronous mode pending transactions
    private val pendingTransactionThreadMap = mutableMapOf<Long, PendingTransaction>()

    actual fun <R> useConnection(
        transactionMode: TransactionMode,
        block: (Connection) -> R,
    ): R {
        val threadId = Thread.currentThread().id
        val transaction: PendingTransaction = pendingTransactionThreadMap.computeIfAbsent(threadId) {
            PendingTransaction(dataSource.connection)
        }

        //TODO: check if we need to setup SQLite change tracking
        transaction.depth.incrementAndGet()
        try {
            return block(transaction.connection)
        }catch(t: Throwable) {
            if(!transaction.connection.autoCommit)
                transaction.connection.rollback()

            throw t
        } finally {
            if(transaction.depth.decrementAndGet() == 0) {
                pendingTransactionThreadMap.remove(threadId)
                transaction.connection.close()
            }
        }
    }

    actual fun <R> useConnection(block: (Connection) -> R) = useConnection(TransactionMode.READ_WRITE, block)

}