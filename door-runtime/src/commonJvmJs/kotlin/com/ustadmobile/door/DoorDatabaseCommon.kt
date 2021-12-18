package com.ustadmobile.door
import com.ustadmobile.door.ext.*
import io.github.aakira.napier.Napier
import com.ustadmobile.door.jdbc.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Runnable
import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.DoorTag

abstract class DoorDatabaseCommon {

    abstract val dataSource: DataSource

    abstract val dbVersion: Int

    abstract val jdbcDbType: Int

    private val transactionRootDatabase: DoorDatabase
        get() {
            @Suppress("CAST_NEVER_SUCCEEDS") //In reality it will succeed
            var db = (this as DoorDatabase)
            while(db is DoorDatabaseRepository || db is DoorDatabaseReplicateWrapper) {
                db = db.sourceDatabase ?: throw IllegalStateException("sourceDatabase cannot be null on repo or wrapper")
            }

            return db
        }

    /**
     * Convenience variable that will be the sourceDatabase if it is not null, or this database
     * itself otherwise
     */
    @Suppress("CAST_NEVER_SUCCEEDS") // This is incorrect
    protected val effectiveDatabase: DoorDatabase
        get() = (this as DoorDatabase).sourceDatabase ?: (this as DoorDatabase)


    @Suppress("CAST_NEVER_SUCCEEDS")
    var arraySupported: Boolean = false
        get() = (this as DoorDatabase).sourceDatabase?.arraySupported ?: field
        private set

    abstract val jdbcArraySupported: Boolean

    abstract val tableNames: List<String>

    val changeListeners = concurrentSafeListOf<ChangeListenerRequest>()

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


    open fun openConnection() =  effectiveDatabase.dataSource.getConnection()

    abstract fun createAllTables(): List<String>

    open fun runInTransaction(runnable: Runnable) {
        runnable.run()
    }


    open fun addChangeListener(changeListenerRequest: ChangeListenerRequest) = transactionRootDatabase.apply {
        changeListeners.add(changeListenerRequest)
    }

    open fun removeChangeListener(changeListenerRequest: ChangeListenerRequest) = transactionRootDatabase.apply {
        changeListeners.remove(changeListenerRequest)
    }


    open fun handleTableChangedInternal(changeTableNames: List<String>) : DoorDatabase{
        GlobalScope.launch {
            effectiveDatabase.apply {
                val affectedChangeListeners = changeListeners.filter {
                    it.tableNames.isEmpty() || it.tableNames.any { changeTableNames.contains(it) }
                }
                Napier.d("$this notifying ${affectedChangeListeners.size} listeners of changes to " +
                        changeTableNames.joinToString(), tag = DoorTag.LOG_TAG)
                affectedChangeListeners.forEach {
                    it.onInvalidated.onTablesInvalidated(changeTableNames)
                }
            }
        }

        return effectiveDatabase
    }

    /**
     * Execute a batch of SQL Statements in a transaction. This is generally much faster
     * than executing statements individually.
     */
    open fun execSQLBatch(vararg sqlStatements: String) {
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
    internal fun Connection.prepareStatement(stmtConfig: PreparedStatementConfig) : PreparedStatement {
        val pgSql = stmtConfig.postgreSql
        val sqlToUse = if(pgSql != null && jdbcDbType == DoorDbType.POSTGRES ) {
            pgSql
        }else {
            stmtConfig.sql
        }

        return when {
            !stmtConfig.hasListParams -> prepareStatement(sqlToUse, stmtConfig.generatedKeys)
            jdbcArraySupported -> prepareStatement(adjustQueryWithSelectInParam(sqlToUse))
            else -> PreparedStatementArrayProxy(sqlToUse, this)
        }
    }


    /**
     * Wrapper for Connection.createArrayOf. If the underlying database supports jdbc arrays, that support will be
     * used. Otherwise the PreparedStatementArrayProxy type will be used
     */
    @Suppress("RemoveRedundantQualifierName") // It's important to be sure which one we are referring to here
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

    override fun toString(): String {
        val name = when {
            this is DoorDatabaseRepository -> this.dbName
            this is DoorDatabaseReplicateWrapper -> this.dbName
            this is DoorDatabaseJdbc -> this.dbName
            else -> "Unknown"
        }
        return "${this::class.simpleName}: $name@${this.doorIdentityHashCode}"
    }
}