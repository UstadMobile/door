package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.sqljsjdbc.SQLiteDatasourceJs
import com.ustadmobile.door.util.TransactionMode


//actual suspend fun <R> DoorDatabaseJdbc.useConnectionAsync(
//    transactionMode: TransactionMode,
//    block: suspend (Connection) -> R,
//): R {
//    val sqliteDs = dataSource as SQLiteDatasourceJs
//    return dataSource.getConnection().useConnectionAsync { connection ->
//        sqliteDs.withTransactionLock {
//            block(connection).also {
//                if(!isInTransaction && invalidationTracker.active) {
//                    val thisDb = this@useConnectionAsync as DoorDatabase
//                    val dbMetaData = thisDb::class.doorDatabaseMetadata()
//                    val changedTables = sqliteDs.findUpdatedTables(dbMetaData)
//                    invalidationTracker.onTablesInvalidated(changedTables.toSet())
//                }
//            }
//        }
//    }
//}
