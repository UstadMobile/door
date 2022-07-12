package androidx.room

import com.ustadmobile.door.RoomJdbcImpl
import com.ustadmobile.door.ext.useStatement
import com.ustadmobile.door.ext.useStatementAsync
import com.ustadmobile.door.jdbc.ext.executeUpdateAsync
import kotlinx.coroutines.Runnable

actual abstract class RoomDatabase actual constructor() {

    actual open class Builder<T : RoomDatabase> {

    }

    actual abstract fun clearAllTables()

    actual abstract val invalidationTracker: InvalidationTracker

    abstract fun createAllTables(): List<String>

    abstract val dbVersion: Int

    abstract suspend fun clearAllTablesAsync()

    open fun runInTransaction(runnable: Runnable) {
        runnable.run()
    }

    suspend fun execSQLBatchAsyncJs(vararg sqlStatements: String){
        (this as RoomJdbcImpl).jdbcImplHelper.useConnectionAsync { connection ->
            connection.createStatement().useStatementAsync {  statement ->
                statement.executeUpdateAsync(sqlStatements.joinToString(";"))
                //(statement as SQLiteStatementJs).executeUpdateAsyncJs(sqlStatements.joinToString(";"))
            }
        }
    }



}