package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.util.SqliteChangeTracker
import com.ustadmobile.door.util.TransactionMode

actual fun <R> DoorDatabaseJdbc.useConnection(
    transactionMode: TransactionMode,
    block: (Connection) -> R
): R {
    val useSqliteChangeTracker = (this as DoorDatabase).dbType() == DoorDbType.SQLITE && !isInTransaction

    val changedTables = mutableSetOf<String>()
    return dataSource.connection.useConnection { connection ->
        lateinit var sqliteChangeTracker : SqliteChangeTracker
        if(useSqliteChangeTracker) {
            sqliteChangeTracker = SqliteChangeTracker((this as DoorDatabase)::class.doorDatabaseMetadata())
            sqliteChangeTracker.setupTriggersOnConnection(connection)
        }

        block(connection).also {
            if(useSqliteChangeTracker) {
                changedTables.addAll(sqliteChangeTracker.findChangedTablesOnConnection(connection))
            }
        }

    }.also {
        if(useSqliteChangeTracker) {
            invalidationTracker.onTablesInvalidated(changedTables)
        }
    }
}


actual suspend fun <R> DoorDatabaseJdbc.useConnectionAsync(
    transactionMode: TransactionMode,
    block: suspend (Connection) -> R
): R {
    val useSqliteChangeTracker = (this as DoorDatabase).dbType() == DoorDbType.SQLITE && !isInTransaction

    val changedTables = mutableSetOf<String>()
    return dataSource.connection.useConnectionAsync { connection ->
        lateinit var sqliteChangeTracker : SqliteChangeTracker
        if(useSqliteChangeTracker) {
            sqliteChangeTracker = SqliteChangeTracker((this as DoorDatabase)::class.doorDatabaseMetadata())
            sqliteChangeTracker.setupTriggersOnConnection(connection)
        }

        block(connection).also {
            if(useSqliteChangeTracker) {
                changedTables.addAll(sqliteChangeTracker.findChangedTablesOnConnection(connection))
            }
        }

    }.also {
        if(useSqliteChangeTracker) {
            invalidationTracker.onTablesInvalidated(changedTables)
        }
    }
}