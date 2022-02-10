package com.ustadmobile.door

import com.ustadmobile.door.ext.rootDatabase
import com.ustadmobile.door.ext.sourceDatabase
import com.ustadmobile.door.ext.useConnection
import com.ustadmobile.door.ext.useStatement
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.SQLException
import kotlinx.coroutines.Runnable
import com.ustadmobile.door.sqljsjdbc.SQLiteConnectionJs
import com.ustadmobile.door.sqljsjdbc.SQLiteDatabaseMetadataJs
import com.ustadmobile.door.sqljsjdbc.SQLiteStatementJs
import kotlin.jvm.Volatile

actual abstract class DoorDatabase actual constructor(): DoorDatabaseCommon() {

    override val jdbcDbType: Int = DoorDbType.SQLITE

    @Volatile
    override val jdbcArraySupported: Boolean = false

    actual abstract fun clearAllTables()

    abstract suspend fun clearAllTablesAsync()

    actual override fun runInTransaction(runnable: Runnable) {
        super.runInTransaction(runnable)
    }

    override val tableNames: List<String>
        get() = throw Exception("This can't be used in JS, only on JVM use getTableNamesAsync")

    suspend fun getTableNamesAsync(): List<String> {
        return if(this != rootDatabase) {
            rootDatabase.getTableNamesAsync()
        } else {
            val tableNamesList = mutableListOf<String>()
            rootDatabaseJdbc.useConnection { con ->
                val metadata = con.getMetaData() as SQLiteDatabaseMetadataJs
                val tableResult = metadata.getTablesAsync(null, null, "%", arrayOf("TABLE"))
                while(tableResult.next()) {
                    tableResult.getString("TABLE_NAME")?.also {
                        tableNamesList.add(it)
                    }
                }
            }

            tableNamesList.toList()
        }
    }

    suspend fun execSQLBatchAsync(vararg sqlStatements: String){
        transactionRootJdbcDb.useConnection { connection ->
            connection.createStatement().useStatement { statement ->
                (statement as SQLiteStatementJs).executeUpdateAsync(sqlStatements.joinToString(";"))
            }
        }
    }

    protected fun setupFromDataSource() {
        //ON JS: nothing to do here - it is always SQLite
    }

    suspend fun exportDatabase() {
        transactionRootJdbcDb.useConnection { connection ->
            (connection as SQLiteConnectionJs).datasource.exportDatabaseToFile()
        }
    }
}