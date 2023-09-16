package com.ustadmobile.door.room

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.util.TransactionMode

/**
 * Contains logic that is used by generated JDBC implementations on JVM and JS. The reason this is not in the parent
 * RoomDatabase class is because we want to be 100% sure that there is only one instance of this class per database
 * instance e.g. one set of invalidation listeners, one map of thread ids to transaction connections, etc.
 */
actual class RoomDatabaseJdbcImplHelper actual constructor(
    dataSource: DataSource,
    db: RoomDatabase,
    dbUrl: String,
    tableNames: List<String>,
    invalidationTracker: InvalidationTracker,
    dbType: Int,
) : RoomDatabaseJdbcImplHelperCommon(dataSource, db, tableNames, invalidationTracker, dbType) {

    override suspend fun Connection.setupSqliteTriggersAsync() {
        //do nothing - this should already be done by the database builder
    }

    actual fun <R> useConnection(
        transactionMode: TransactionMode,
        block: (Connection) -> R
    ): R {
        throw IllegalStateException("useConnection synchronous not supported on JS")
    }

    /**
     *
     */
    actual fun <R> useConnection(block: (Connection) -> R): R {
        throw IllegalStateException("useConnection synchronous not supported on JS")
    }

}