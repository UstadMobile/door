package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.sqljsjdbc.SQLiteConnectionJs
import com.ustadmobile.door.sqljsjdbc.SQLiteDatasourceJs
import com.ustadmobile.door.util.SqliteChangeTracker
import com.ustadmobile.door.util.TransactionMode

actual fun <R> DoorDatabaseJdbc.useConnection(
    transactionMode: TransactionMode,
    block: (Connection) -> R,
): R {
    throw SQLException("Synchronous useConnection is not supported on Javascript!")
}

actual suspend fun <R> DoorDatabaseJdbc.useConnectionAsync(
    transactionMode: TransactionMode,
    block: suspend (Connection) -> R,
): R {
    val sqliteDs = dataSource as SQLiteDatasourceJs
    return dataSource.getConnection().useConnectionAsync { connection ->
        sqliteDs.withTransactionLock {
            block(connection).also {
                if(!isInTransaction && sqliteDs.changeTrackingEnabled) {
                    val thisDb = this@useConnectionAsync as DoorDatabase
                    val dbMetaData = thisDb::class.doorDatabaseMetadata()
                    val changedTables = sqliteDs.findUpdatedTables(dbMetaData)
                    invalidationTracker.onTablesInvalidated(changedTables.toSet())
                }
            }
        }
    }
}
