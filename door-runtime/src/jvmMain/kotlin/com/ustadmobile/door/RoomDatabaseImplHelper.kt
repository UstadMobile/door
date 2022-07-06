package com.ustadmobile.door

import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import kotlinx.atomicfu.atomic

/**
 * Used by generated code. We don't put this directly in the parent RoomDatabase class, because then it would have to
 * be implemented by Repositories etc.
 */
class RoomDatabaseImplHelper(
    private val dataSource: DataSource
) {

    inner class PendingTransaction(
        val connection: Connection
    ) {
        val depth = atomic(0)
    }

    //Synchronous mode pending transactions
    val pendingTransactionThreadMap = concurrentSafeMapOf<Long, PendingTransaction>()

    //Async mode pending transactions
    val pendingTransactionCoroutineMap = concurrentSafeMapOf<Long, PendingTransaction>()

    fun <R> useConnection(
        block: (Connection) -> R
    ) : R {
        val threadId = Thread.currentThread().id
        val transaction: PendingTransaction = pendingTransactionThreadMap.computeIfAbsent(threadId) {
            PendingTransaction(dataSource.connection)
        }

        transaction.depth.incrementAndGet()
        try {
            return block(transaction.connection)
        }finally {
            if(transaction.depth.decrementAndGet() == 0) {
                pendingTransactionThreadMap.remove(threadId)
                transaction.connection.close()
            }
        }
    }


}