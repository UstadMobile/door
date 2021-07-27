package com.ustadmobile.door

import com.ustadmobile.door.ext.useConnection
import com.ustadmobile.door.jdbc.SQLException
import kotlinx.coroutines.Runnable
import wrappers.SQLiteConnectionJs
import wrappers.SQLitePreparedStatementJs
import kotlin.jvm.Volatile

actual abstract class DoorDatabase actual constructor(): DoorDatabaseCommon() {

    override var jdbcDbType: Int = -1
        get() = sourceDatabase?.jdbcDbType ?: field
        protected set

    @Volatile
    override var jdbcArraySupported: Boolean = false
        get() = sourceDatabase?.jdbcArraySupported ?: field
        protected set

    actual abstract fun clearAllTables()

    actual override fun runInTransaction(runnable: Runnable) {
        super.runInTransaction(runnable)
    }

    suspend fun execSQLBatchAsync(vararg sqlStatements: String){
        var connection: SQLiteConnectionJs? = null
        try {
            connection = openConnection() as SQLiteConnectionJs
            connection.setAutoCommit(false)
            sqlStatements.first().split("#").forEach { sql ->
                val statement = SQLitePreparedStatementJs(connection,sql)
                val result = statement.executeUpdateAsync()
                if(result != -1){
                    statement.close()
                }
            }
        }catch(e: SQLException) {
            throw e
        }finally {
            connection?.close()
        }
    }

    protected fun setupFromDataSource() {
        openConnection().useConnection { dbConnection ->
            jdbcDbType = DoorDbType.typeIntFromProductName(dbConnection.getMetaData().databaseProductName)
            jdbcArraySupported = jdbcDbType == DoorDbType.SQLITE
        }
    }
}