package com.ustadmobile.door.transaction

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.SQLException

/**
 * This is a wrapper for the DataSource class that can be used with the JDBC implementation to run transactions. This
 * DataSource will call the real DataSource getConnection once and only once.
 *
 * It will use a single connection and calls to getConnection will return a connection wrapper. The wrapper will set
 * autoCommit set to false on the real underlying connection at the start. When the transaction is completed via the use
 * or useAsync function, the commit function on the real underlying connection will be called and its autoCommit will be
 * restored to being true. The returned connection wrapper will block calls to commit, close, and rollback.
 */
class DoorTransactionDataSourceWrapper(
    private val realDataSource: DataSource
): DataSource by realDataSource  {

    @Volatile
    private var transactionClosed: Boolean = false

    private class TransactionConnectionWrapper(
        val realConnection: Connection
    ) : Connection by realConnection {

        override fun setAutoCommit(autoCommit: Boolean) {
            //Block this
        }

        override fun commit() {
            //Block this
        }

        override fun rollback() {
            //Block this
        }

        override fun close() {
            //Do nothing
        }
    }

    private val transactionConnectionWrapper: TransactionConnectionWrapper by lazy {
        TransactionConnectionWrapper(realDataSource.connection.also {
            it.autoCommit = false
        })
    }

    override fun getConnection(): Connection {
        return transactionConnectionWrapper
    }

    override fun getConnection(p0: String?, p1: String?): Connection {
        throw SQLException("TransactionDataSource: using username/password not supported currently.")
    }

    fun <R> use(block: (DataSource) -> R): R {
        if(transactionClosed)
            throw SQLException("TransactionDataSource is closed!")

        try {
            val result = block(this)
            commit()
            return result
        }catch(e: Throwable) {
            rollback()
            throw e
        }finally {
            transactionClosed = true
            transactionConnectionWrapper.realConnection.close()
        }
    }

    suspend fun <R> useAsync(block: suspend (DataSource) -> R): R {
        if(transactionClosed)
            throw SQLException("TransactionDataSource is closed!")

        try {
            val result = block(this)
            commit()
            return result
        }catch(e: Throwable) {
            rollback()
            throw e
        }finally {
            transactionClosed = true
            transactionConnectionWrapper.realConnection.close()
        }
    }

    private fun commit() {
        transactionConnectionWrapper.realConnection.commit()
    }

    private fun rollback() {
        transactionConnectionWrapper.realConnection.rollback()
    }
}