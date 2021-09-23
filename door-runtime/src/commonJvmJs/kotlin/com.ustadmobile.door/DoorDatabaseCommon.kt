package com.ustadmobile.door
import com.ustadmobile.door.jdbc.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Runnable
import com.ustadmobile.door.ext.concurrentSafeListOf

abstract class DoorDatabaseCommon {

    abstract val dataSource: DataSource

    abstract val dbVersion: Int

    abstract val jdbcDbType: Int

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
        get() = sourceDatabase ?: (this as DoorDatabase)


    var arraySupported: Boolean = false
        get() = sourceDatabase?.arraySupported ?: field
        private set

    abstract val jdbcArraySupported: Boolean

    val changeListeners = concurrentSafeListOf<ChangeListenerRequest>() as MutableList<ChangeListenerRequest>

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
                val metadata = con.getMetaData()
                tableResult = metadata.getTables(null, null, "%", arrayOf("TABLE"))
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

    inner class DoorSqlDatabaseImpl : DoorSqlDatabase {

        override fun execSQL(sql: String) {
            this@DoorDatabaseCommon.execSQLBatch(sql)
        }

        override fun execSQLBatch(statements: kotlin.Array<String>) {
            this@DoorDatabaseCommon.execSQLBatch(*statements)
        }

        val jdbcDbType: Int
            get() = this@DoorDatabaseCommon.jdbcDbType

    }

    internal val sqlDatabaseImpl = DoorSqlDatabaseImpl()


    /**
     * Postgres queries with array parameters (e.g. SELECT IN (?) need to be adjusted
     */
    fun adjustQueryWithSelectInParam(querySql: String): String {
        return if(jdbcDbType == DoorDbType.POSTGRES) {
            POSTGRES_SELECT_IN_PATTERN.replace(querySql, POSTGRES_SELECT_IN_REPLACEMENT)
        }else {
            querySql
        }
    }


    fun openConnection() =  effectiveDatabase.dataSource.getConnection()

    abstract fun createAllTables(): List<String>

    open fun runInTransaction(runnable: Runnable) {
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
            connection.setAutoCommit(false)
            statement = connection.createStatement()
            sqlStatements.forEach { sql ->
                statement.executeUpdate(sql)
            }
            connection.commit()
        }catch(e: SQLException) {
            throw e
        }finally {
            statement?.close()
            connection?.setAutoCommit(true)
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
            !stmtConfig.hasListParams -> prepareStatement(stmtConfig.sql, stmtConfig.generatedKeys)
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
     * Wrapper for Connection.createArrayOf. If the underlying database supports jdbc arrays, that support will be
     * used. Otherwise the PreparedStatementArrayProxy type will be used
     */
    fun createArrayOf(connection: Connection, arrayType: String, objects: kotlin.Array<out Any?>): com.ustadmobile.door.jdbc.Array {
        return if(jdbcArraySupported) {
            connection.createArrayOf(arrayType, objects)
        }else {
            JdbcArrayProxy(arrayType, objects)
        }
    }

    companion object {
        const val DBINFO_TABLENAME = "_doorwayinfo"

        const val POSTGRES_SELECT_IN_REPLACEMENT = "IN (SELECT UNNEST(?))"

        val POSTGRES_SELECT_IN_PATTERN = Regex("IN(\\s*)\\((\\s*)\\?(\\s*)\\)",
            RegexOption.IGNORE_CASE)
    }



}