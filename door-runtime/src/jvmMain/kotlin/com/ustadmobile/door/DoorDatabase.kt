package com.ustadmobile.door


import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern
import javax.sql.DataSource

actual abstract class DoorDatabase actual constructor(){

    protected lateinit var dataSource: DataSource

    abstract val dbVersion: Int

    /**
     * Sometimes we want to create a new instance of the database that is just a wrapper e.g.
     * SyncableReadOnlyWrapper, possibly a transaction wrapper. When this happens, all calls to
     * listen for changes, opening connections, etc. should be redirected to the source database
     */
    var sourceDatabase: DoorDatabase? = null
        protected set

    /**
     * Convenience variable that will be the sourceDatabase if it is not null, or this database
     * itself otherwise
     */
    protected val effectiveDatabase: DoorDatabase
        get() = sourceDatabase ?: this

    var jdbcDbType: Int = -1
        get() = sourceDatabase?.jdbcDbType ?: field
        protected set

    var arraySupported: Boolean = false
        get() = sourceDatabase?.arraySupported ?: field
        private set

    val jdbcArraySupported: Boolean by lazy {
        val delegatedDatabaseVal = sourceDatabase
        if(delegatedDatabaseVal != null) {
            delegatedDatabaseVal.jdbcArraySupported
        }else {
            var connection = null as Connection?
            var sqlArray = null as java.sql.Array?
            try {
                connection = openConnection()
                sqlArray = connection?.createArrayOf("VARCHAR", arrayOf("hello"))
            }finally {
                connection?.close()
            }

            sqlArray != null
        }


    }

    /**
     * A request to listen for changes. This is used by LiveData and other items. The onChange
     * function will be run when a table is changed.
     *
     * @param tableNames A list (case sensitive) of the table names on which this listener should be invoked.
     * An empty list will result in the onChange method always being called
     *
     * @param onChange A function that will be executed when after a change has happened on a table.
     */
    data class ChangeListenerRequest(val tableNames: List<String>, val onChange: (List<String>) -> Unit)

    val changeListeners = CopyOnWriteArrayList<ChangeListenerRequest>() as MutableList<ChangeListenerRequest>

    val tableNames: List<String> by lazy {
        val delegatedDatabaseVal = sourceDatabase
        if(delegatedDatabaseVal != null) {
            delegatedDatabaseVal.tableNames
        }else {
            var con = null as Connection?
            val tableNamesList = mutableListOf<String>()
            var tableResult = null as ResultSet?
            try {
                con = openConnection()
                val metadata = con.metaData
                tableResult = metadata.getTables(null, null, "%", arrayOf("TABLE"))
                while(tableResult.next()) {
                    tableNamesList.add(tableResult.getString("TABLE_NAME"))
                }
            }finally {
                con?.close()
            }

            tableNamesList.toList()
        }

    }

    inner class DoorSqlDatabaseImpl : DoorSqlDatabase {

        override fun execSQL(sql: String) {
            this@DoorDatabase.execSQLBatch(sql)
        }

        override fun execSQLBatch(statements: Array<String>) {
            this@DoorDatabase.execSQLBatch(*statements)
        }

        val jdbcDbType: Int
            get() = this@DoorDatabase.jdbcDbType

    }

    internal val sqlDatabaseImpl = DoorSqlDatabaseImpl()

    protected fun setupFromDataSource() {
        var dbConnection = null as Connection?
        try{
            dbConnection = openConnection()
            jdbcDbType = DoorDbType.typeIntFromProductName(dbConnection.metaData?.databaseProductName ?: "")
            arraySupported = jdbcDbType == DoorDbType.POSTGRES
        }finally {
            dbConnection?.close()
        }
    }

    /**
     * Postgres queries with array parameters (e.g. SELECT IN (?) need to be adjusted
     */
    fun adjustQueryWithSelectInParam(querySql: String): String {
        return if(jdbcDbType == DoorDbType.POSTGRES) {
            POSTGRES_SELECT_IN_PATTERN.matcher(querySql).replaceAll(POSTGRES_SELECT_IN_REPLACEMENT)
        }else {
            querySql
        }
    }


    fun openConnection() =  effectiveDatabase.dataSource.connection

    abstract fun createAllTables()

    actual abstract fun clearAllTables()

    actual open fun runInTransaction(runnable: Runnable) {
        runnable.run()
    }

    open fun addChangeListener(changeListenerRequest: ChangeListenerRequest) = effectiveDatabase.apply {
        changeListeners.add(changeListenerRequest)
    }

    open fun removeChangeListener(changeListenerRequest: ChangeListenerRequest) = effectiveDatabase.apply {
        changeListeners.remove(changeListenerRequest)
    }


    open fun handleTableChanged(changeTableNames: List<String>) = effectiveDatabase.apply {
        GlobalScope.launch {
            changeListeners.filter { it.tableNames.isEmpty() || it.tableNames.any { changeTableNames.contains(it) } }.forEach {
                it.onChange.invoke(changeTableNames)
            }
        }
    }

    /**
     * Execute a batch of SQL Statements in a transaction. This is generally much faster
     * than executing statements individually.
     */
    fun execSQLBatch(vararg sqlStatements: String) {
        var connection: Connection? = null
        var statement: Statement? = null
        try {
            connection = openConnection()
            connection.autoCommit = false
            statement = connection.createStatement()
            sqlStatements.forEach { sql ->
                statement.executeUpdate(sql)
            }
            connection.commit()
        }catch(e: SQLException) {
            throw e
        }finally {
            statement?.close()
            connection?.autoCommit = true
            connection?.close()
        }
    }

    companion object {
        const val DBINFO_TABLENAME = "_doorwayinfo"

        const val POSTGRES_SELECT_IN_REPLACEMENT = "IN (SELECT UNNEST(?))"

        val POSTGRES_SELECT_IN_PATTERN = Pattern.compile("IN(\\s*)\\((\\s*)\\?(\\s*)\\)",
                Pattern.CASE_INSENSITIVE)
    }

}