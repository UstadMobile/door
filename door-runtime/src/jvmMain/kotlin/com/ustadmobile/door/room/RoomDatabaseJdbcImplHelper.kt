package com.ustadmobile.door.room

import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.ext.rootDatabase
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.ext.mutableLinkedListOf
import com.ustadmobile.door.log.DoorLogger
import com.ustadmobile.door.log.d
import com.ustadmobile.door.log.e
import com.ustadmobile.door.util.PostgresChangeTracker
import com.ustadmobile.door.util.systemTimeInMillis
import com.zaxxer.hikari.HikariDataSource
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

actual class RoomDatabaseJdbcImplHelper actual constructor(
    dataSource: DataSource,
    db: RoomDatabase,
    private val dbUrl: String,
    dbName: String,
    logger: DoorLogger,
    tableNames: List<String>,
    invalidationTracker: InvalidationTracker,
    dbType: Int,
) : RoomDatabaseJdbcImplHelperCommon(
    dataSource = dataSource,
    db = db,
    dbName = dbName,
    logger = logger,
    tableNames = tableNames,
    invalidationTracker = invalidationTracker,
    dbType = dbType
) {



    inner class PendingTransaction(val connection: Connection, val readOnly: Boolean)

    private val postgresChangeTracker = if(dbType == DoorDbType.POSTGRES) {
        PostgresChangeTracker(dataSource, invalidationTracker, tableNames)
    }else {
        null
    }

    override fun onStartChangeTracking() {
        postgresChangeTracker?.setupTriggers()
    }

    override suspend fun Connection.setupSqliteTriggersAsync() {
        invalidationTracker.setupSqliteTriggersAsync(this)
    }

    private val pendingTransactionThreadMap = concurrentSafeMapOf<Long, PendingTransaction>()

    private fun <R> useNewConnectionInternal(
        readOnly: Boolean,
        block: (Connection) -> R,
        threadId: Long,
    ): R {
        assertNotClosed()
        val funLogPrefix = "$logPrefix - newConnectionInternal - Thread #$threadId"
        val startTime = systemTimeInMillis()
        val transaction = PendingTransaction(dataSource.connection, readOnly)
        transaction.connection.takeIf { !readOnly }?.autoCommit = false
        pendingTransactionThreadMap[threadId] = transaction
        return try {
            if(!readOnly && dbType == DoorDbType.SQLITE) {
                invalidationTracker.setupSqliteTriggers(transaction.connection)
            }

            val changedTables = mutableLinkedListOf<String>()
            block(transaction.connection).also {
                if(!readOnly && dbType == DoorDbType.SQLITE) {
                    changedTables.addAll(invalidationTracker.findChangedTablesOnConnection(transaction.connection))
                }

                transaction.connection.takeIf { !readOnly }?.commit()

                invalidationTracker.takeIf { changedTables.isNotEmpty() }?.onTablesInvalidated(changedTables.toSet())
            }
        }catch(e: Exception) {
            logger.e("$funLogPrefix exception", e)
            if(!readOnly) {
                transaction.connection.rollback()
            }

            throw e
        }finally {
            transaction.connection.close()
            pendingTransactionThreadMap.remove(threadId)
            if(pendingTransactionThreadMap.isNotEmpty()) {
                logger.d("useConnection: close connection for thread #$threadId (took ${systemTimeInMillis() - startTime}ms)" +
                        " There are ${pendingTransactionThreadMap.size} pending non-async transactions still open.")
            }
        }
    }

    actual fun <R> useConnection(
        readOnly: Boolean,
        block: (Connection) -> R,
    ): R {
        val threadId = Thread.currentThread().id
        val threadPendingTransaction = pendingTransactionThreadMap[threadId]
        val dbQueryTimeoutMs = ((db.rootDatabase as DoorDatabaseJdbc).jdbcQueryTimeout * 1000).toLong()

        return if(threadPendingTransaction != null) {
            if(threadPendingTransaction.readOnly && !readOnly) {
                throw IllegalStateException("useConnection: current connection is readOnly, cannot request a " +
                        "read/write connection in the same transaction")
            }

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
                        useNewConnectionInternal(readOnly, block, threadId)
                    }
                }
            }
        }else {
            useNewConnectionInternal(readOnly, block, threadId)
        }
    }

    actual fun <R> useConnection(block: (Connection) -> R) = useConnection(false, block)

    override fun onClose() {
        pendingTransactionThreadMap.forEach {
            try {
                it.value.connection.close()
            }catch(e: Exception) {
                Napier.w(tag = DoorTag.LOG_TAG, throwable = e) {
                    "${this.db} exception closing transactions for thread #${it.key}"
                }
            }
        }
        pendingTransactionThreadMap.clear()

        postgresChangeTracker?.close()

        // If the DBURL is jdbc: then the datasource was created by the DatabaseBuilder, otherwise it was looked up
        // via JNDI. If the DataSource was created by the builder and is a datapool, then it must be closed so
        // connections are released
        if(dbUrl.startsWith("jdbc:") && dataSource is HikariDataSource) {
            dataSource.close()
            Napier.d(tag = DoorTag.LOG_TAG, message = "Closed HikariDataSource connection pool")
        }
    }
}