package com.ustadmobile.door.room

import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.ext.mutableLinkedListOf
import com.ustadmobile.door.util.TransactionMode
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

abstract class RoomDatabaseJdbcImplHelperCommon(
    protected val dataSource: DataSource,
    protected val db: RoomDatabase,
    private val tableNames: List<String>,
    val invalidationTracker: InvalidationTracker,
) {

    open val dbType: Int = DoorDbType.SQLITE

    class TransactionElement(
        override val key: Key,
        val connection: Connection,
    ) : CoroutineContext.Element

    /**
     * Setup triggers (if needed) for SQLite change tracking. On JVM (where multiple connections can operate run at
     * the same time
     */
    abstract suspend fun Connection.setupSqliteTriggersAsync()

    @Suppress("UNUSED_PARAMETER") //Reserved for future usage
    suspend fun <R> useConnectionAsync(
        transactionMode: TransactionMode,
        block: suspend (Connection) -> R
    ): R {
        val transactionContext = coroutineContext.get(Key)

        val blockRunner: suspend (TransactionElement) -> R = {txContext ->
            withContext(txContext) {
                //if dbtype is sqlite, setup the change trackers.
                block(txContext.connection)
            }
        }

        return if(transactionContext != null) {
            //continue using existing connection for the transaction
            blockRunner(transactionContext)
        }else {
            //get a new connection for a new transaction context
            val connection = dataSource.getConnection()
            connection.setAutoCommit(false)

            val changedTables = mutableLinkedListOf<String>()
            try {
                if(dbType == DoorDbType.SQLITE) {
                    connection.setupSqliteTriggersAsync()
                }

                val result = blockRunner(TransactionElement(Key, connection))

                if(dbType == DoorDbType.SQLITE) {
                    changedTables.addAll(invalidationTracker.findChangedTablesOnConnectionAsync(connection))
                }

                connection.commit()

                invalidationTracker.takeIf { changedTables.isNotEmpty() }?.onTablesInvalidated(changedTables.toSet())

                result
            }catch(t: Throwable) {
                if(!connection.getAutoCommit())
                    connection.rollback()

                throw t
            }finally {
                connection.close()
            }
        }
    }

    suspend fun <R> useConnectionAsync(
        block: suspend (Connection) -> R
    ) = useConnectionAsync(TransactionMode.READ_WRITE, block)

    companion object Key : CoroutineContext.Key<TransactionElement>

}