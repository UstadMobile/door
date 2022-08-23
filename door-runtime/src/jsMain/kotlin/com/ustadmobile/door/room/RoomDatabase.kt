package com.ustadmobile.door.room

import com.ustadmobile.door.jdbc.ext.useStatementAsync
import kotlinx.coroutines.Runnable

actual abstract class RoomDatabase actual constructor() {

    actual abstract fun clearAllTables()

    actual open fun getInvalidationTracker(): InvalidationTracker {
        TODO("getInvalidationTracker: maybe override this in the generated version")
    }

    abstract fun createAllTables(): List<String>

    abstract val dbVersion: Int

    abstract suspend fun clearAllTablesAsync()

    open fun runInTransaction(runnable: Runnable) {
        runnable.run()
    }

    suspend fun execSQLBatchAsyncJs(vararg sqlStatements: String){
        (this as RoomJdbcImpl).jdbcImplHelper.useConnectionAsync { connection ->
            connection.createStatement().useStatementAsync {  statement ->
                statement.executeUpdateAsyncJs(sqlStatements.joinToString(";"))
            }
        }
    }



}