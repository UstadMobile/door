package com.ustadmobile.door

import androidx.room.RoomDatabase
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.util.TransactionMode


/**
 * Contains logic that is used by generated JDBC implementations on JVM and JS. The reason this is not in the parent
 * RoomDatabase class is because we want to be 100% sure that there is only one instance of this class per database
 * instance e.g. one set of invalidation listeners, one map of thread ids to transaction connections, etc.
 */
expect class RoomDatabaseJdbcImplHelper(
    dataSource: DataSource,
    db: RoomDatabase,
    tableNames: List<String>,
) : RoomDatabaseJdbcImplHelperCommon {

    fun <R> useConnection(
        transactionMode: TransactionMode,
        block: (Connection) -> R
    ) : R

    /**
     *
     */
    fun <R> useConnection(block: (Connection) -> R) : R

}