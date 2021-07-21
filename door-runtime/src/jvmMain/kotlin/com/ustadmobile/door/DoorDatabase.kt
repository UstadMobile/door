package com.ustadmobile.door


import com.ustadmobile.door.jdbc.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern
import javax.sql.DataSource

@Suppress("unused") //Some functions are used by generated code
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

    /**
     * Convenience extension function that will create a prepared statement. It will
     * use builtin support for arrays if required and available, or fallback to using
     * PreparedStatementArrayProxy otherwise.
     */
    protected fun Connection.prepareStatement(stmtConfig: PreparedStatementConfig) : PreparedStatement {
        return when {
            !stmtConfig.hasListParams -> prepareStatement(stmtConfig.sql)
            jdbcArraySupported -> prepareStatement(adjustQueryWithSelectInParam(stmtConfig.sql))
            else -> PreparedStatementArrayProxy(stmtConfig.sql, this)
        } ?: throw IllegalStateException("Null statement")
    }

    /**
     * Suspended wrapper that will prepare a Statement, execute a code block, and return the code block result
     */
    fun <R> prepareAndUseStatement(
        sql: String,
        block: (PreparedStatement) -> R
    ) = prepareAndUseStatement(PreparedStatementConfig(sql), block)

    /**
     * Wrapper that will prepare a Statement, execute a code block, and return the code block result
     */
    fun <R> prepareAndUseStatement(stmtConfig: PreparedStatementConfig, block: (PreparedStatement) -> R) : R {
        var connection: Connection? = null
        var stmt: PreparedStatement? = null
        try {
            connection = openConnection()
            stmt = connection.prepareStatement(stmtConfig)
            return block(stmt)
        }finally {
            stmt?.close()
            connection?.close()
        }
    }

    /**
     * Suspended wrapper that will prepare a Statement, execute a code block, and return the code block result
     */
    suspend fun <R> prepareAndUseStatementAsync(
        sql: String,
        block: suspend (PreparedStatement) -> R
    ) = prepareAndUseStatementAsync(PreparedStatementConfig(sql), block)

    /**
     * Suspended wrapper that will prepare a Statement, execute a code block, and return the code block result
     */
    suspend fun <R> prepareAndUseStatementAsync(stmtConfig: PreparedStatementConfig, block: suspend (PreparedStatement) -> R) : R {
        var connection: Connection? = null
        var stmt: PreparedStatement? = null
        try {
            connection = openConnection()
            stmt = connection.prepareStatement(stmtConfig)

            return block(stmt)
        }finally {
            stmt?.close()
            connection?.close()
        }
    }

    /**
     * Wrapper for Connection.createArrayOf. If the underlying database supports jdbc arrays, that support will be
     * used. Otherwise the PreparedStatementArrayProxy type will be used
     */
    fun createArrayOf(connection: Connection, arrayType: String, objects: Array<out Any?>): java.sql.Array {
        return if(jdbcArraySupported) {
            connection.createArrayOf(arrayType, objects)
        }else {
            PreparedStatementArrayProxy.createArrayOf(arrayType, objects)
        }
    }

    companion object {
        const val DBINFO_TABLENAME = "_doorwayinfo"

        const val POSTGRES_SELECT_IN_REPLACEMENT = "IN (SELECT UNNEST(?))"

        val POSTGRES_SELECT_IN_PATTERN = Pattern.compile("IN(\\s*)\\((\\s*)\\?(\\s*)\\)",
                Pattern.CASE_INSENSITIVE)
    }

}