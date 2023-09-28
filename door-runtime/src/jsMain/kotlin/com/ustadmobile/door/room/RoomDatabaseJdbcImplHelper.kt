package com.ustadmobile.door.room

import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.sqljsjdbc.SQLiteDatasourceJs
import io.github.aakira.napier.Napier

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
        readOnly: Boolean,
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


    @Suppress("unused") //API to export database for debugging etc.
    suspend fun exportToFile() {
        (dataSource as SQLiteDatasourceJs).exportDatabaseToFile()
    }


    override fun onClose() {
        super.onClose()

        if(dataSource is SQLiteDatasourceJs) {
            Napier.i(tag = DoorTag.LOG_TAG) { "SQLite/JS Datasource: closing\n" }
            dataSource.close()
            Napier.i(tag = DoorTag.LOG_TAG) { "SQLite/JS Datasource: closed\n" }
        }
    }
}