package com.ustadmobile.door

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.ext.mutableLinkedListOf
import com.ustadmobile.door.util.TransactionMode
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

open class RoomDatabaseJdbcImplHelperCommon(
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
                //TODO: This needs updated to handle JS and JVM. JS might need this created only once at the start.
                if(dbType == DoorDbType.SQLITE) {
                    invalidationTracker.setupSqliteTriggersAsync(connection)
                }

                val result = blockRunner(TransactionElement(Key, connection))

                if(dbType == DoorDbType.SQLITE) {
                    changedTables.addAll(invalidationTracker.findChangedTablesOnConnection(connection))
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