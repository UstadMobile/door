package com.ustadmobile.door

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.util.TransactionMode
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

open class RoomDatabaseJdbcImplHelperCommon(
    protected val dataSource: DataSource,
) {

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
        return if(transactionContext != null) {
            withContext(transactionContext) {
                block(transactionContext.connection)
            }
        }else {
            val connection = dataSource.getConnection()
            try {
                withContext(TransactionElement(Key, connection)) {
                    block(connection)
                }
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