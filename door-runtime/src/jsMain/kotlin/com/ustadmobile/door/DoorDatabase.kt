package com.ustadmobile.door

import com.ustadmobile.door.ext.useConnection
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.SQLException
import kotlinx.coroutines.*
import wrappers.*
import kotlin.jvm.Volatile

actual abstract class DoorDatabase actual constructor(): DoorDatabaseCommon() {

    override val jdbcDbType: Int = DoorDbType.SQLITE

    @Volatile
    override val jdbcArraySupported: Boolean = false

    actual abstract fun clearAllTables()

    actual override fun runInTransaction(runnable: Runnable) {
        super.runInTransaction(runnable)
    }

    override val tableNames: List<String>
        get() = throw Exception("This can't be used in JS, only on JVM use getTableNamesAsync")

    suspend fun getTableNamesAsync(): List<String> {
        val delegatedDatabaseVal = sourceDatabase
        return if(delegatedDatabaseVal != null) {
            delegatedDatabaseVal.getTableNamesAsync()
        } else {
            var con = null as SQLiteConnectionJs?
            val tableNamesList = mutableListOf<String>()
            val tableResult: ResultSet?
            try {
                con = openConnection() as SQLiteConnectionJs
                val metadata = con.getMetaData() as SQLiteDatabaseMetadataJs
                tableResult = metadata.getTablesAsync(null, null, "%", arrayOf("TABLE"))
                while(tableResult.next()) {
                    tableResult.getString("TABLE_NAME")?.also {
                        tableNamesList.add(it)
                    }
                }
            }finally {
                con?.close()
            }

            tableNamesList.toList()
        }
    }

    suspend fun execSQLBatchAsync(vararg sqlStatements: String){
        var connection: SQLiteConnectionJs? = null
        try {
            connection = openConnection() as SQLiteConnectionJs
            connection.setAutoCommit(false)
            val statement = SQLitePreparedStatementJs(connection,sqlStatements.joinToString(";"))
            val result = statement.executeUpdateAsync()
            if(result != -1){
                statement.close()
            }
        }catch(e: SQLException) {
            throw e
        }finally {
            connection?.close()
        }
    }

    protected fun setupFromDataSource() {
        //ON JS: nothing to do here - it is always SQLite
    }
}