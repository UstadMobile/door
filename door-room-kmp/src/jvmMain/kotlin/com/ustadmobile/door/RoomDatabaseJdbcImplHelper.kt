package com.ustadmobile.door

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.ext.mutableLinkedListOf
import com.ustadmobile.door.util.TransactionMode
import kotlinx.atomicfu.atomic

actual class RoomDatabaseJdbcImplHelper actual constructor(
    dataSource: DataSource,
    db: RoomDatabase,
    tableNames: List<String>,
    invalidationTracker: InvalidationTracker,
) : RoomDatabaseJdbcImplHelperCommon(dataSource, db, tableNames, invalidationTracker) {

    inner class PendingTransaction(
        val connection: Connection
    ) {
        val depth = atomic(0)
    }

    override suspend fun Connection.setupSqliteTriggersAsync() {
        invalidationTracker.setupSqliteTriggersAsync(this)
    }

    //Switch this to using concurrentSafeMap
    //Synchronous mode pending transactions
    private val pendingTransactionThreadMap = concurrentSafeMapOf<Long, PendingTransaction>()

    override val dbType: Int by lazy {
        dataSource.connection.use { connection ->
            DoorDbType.typeIntFromProductName(connection.metaData?.databaseProductName ?: "")
        }
    }

    actual fun <R> useConnection(
        transactionMode: TransactionMode,
        block: (Connection) -> R,
    ): R {
        val threadId = Thread.currentThread().id
        val transaction: PendingTransaction = pendingTransactionThreadMap.computeIfAbsent(threadId) {
            val pendingTx = PendingTransaction(dataSource.connection)
            pendingTx.connection.autoCommit = false

            if(dbType == DoorDbType.SQLITE) {
                invalidationTracker.setupSqliteTriggers(pendingTx.connection)
            }

            pendingTx
        }

        transaction.depth.incrementAndGet()
        var err: Throwable? = null
        try {
            return block(transaction.connection)
        }catch(t: Throwable) {
            err = t
            if(!transaction.connection.autoCommit) {
                transaction.connection.rollback()
            }

            throw t
        } finally {
            if(transaction.depth.decrementAndGet() == 0) {
                pendingTransactionThreadMap.remove(threadId)

                val changedTables = mutableLinkedListOf<String>()
                if(dbType == DoorDbType.SQLITE && err == null) {
                    changedTables.addAll(invalidationTracker.findChangedTablesOnConnection(transaction.connection))
                }

                transaction.connection.commit()
                transaction.connection.close()

                invalidationTracker.takeIf { changedTables.isNotEmpty() }?.onTablesInvalidated(changedTables.toSet())
            }
        }
    }

    actual fun <R> useConnection(block: (Connection) -> R) = useConnection(TransactionMode.READ_WRITE, block)

}