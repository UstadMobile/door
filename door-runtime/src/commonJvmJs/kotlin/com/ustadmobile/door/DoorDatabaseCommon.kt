package com.ustadmobile.door
import com.ustadmobile.door.ext.*
import io.github.aakira.napier.Napier
import com.ustadmobile.door.jdbc.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Runnable
import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.rootTransactionDatabase

abstract class DoorDatabaseCommon {

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

    val transactionRootJdbcDb : DoorDatabaseJdbc
        get() = ((this as? DoorDatabase)?.rootTransactionDatabase as? DoorDatabaseJdbc)
            ?: throw IllegalStateException("Database does not have jdbc transaction root")

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

    protected val rootDatabaseJdbc: DoorDatabaseJdbc
        get() = (this as DoorDatabase).rootDatabase as DoorDatabaseJdbc

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
    fun adjustQueryWithSelectInParam(querySql: String): String = querySql.adjustQueryWithSelectInParam(jdbcDbType)

    abstract fun createAllTables(): List<String>

    open fun runInTransaction(runnable: Runnable) {
        runnable.run()
    }


    fun addChangeListener(doorInvalidationObserver: ChangeListenerRequest) {
        rootDatabaseJdbc.invalidationTracker.addInvalidationListener(doorInvalidationObserver)
    }

    fun removeChangeListener(doorInvalidationObserver: ChangeListenerRequest) {
        rootDatabaseJdbc.invalidationTracker.removeInvalidationListener(doorInvalidationObserver)
    }


    fun handleTableChangedInternal(changeTableNames: List<String>) {
        Napier.d("$this : handleTableChanged: ${changeTableNames.joinToString()}")
        transactionRootJdbcDb.invalidationTracker.onTablesInvalidated(changeTableNames.toSet())
    }

    /**
     * Execute a batch of SQL Statements in a transaction. This is generally much faster
     * than executing statements individually.
     */
    open fun execSQLBatch(vararg sqlStatements: String) {
        var connection: Connection? = null
        var statement: Statement? = null
        val rootDb = (this as DoorDatabase).rootDatabase
        val jdbcDb = (rootDb as DoorDatabaseJdbc)
        try {
            connection = jdbcDb.openConnection()
            connection.setAutoCommit(false)
            statement = connection.createStatement()
            sqlStatements.forEach { sql ->
                try {
                    statement.executeUpdate(sql)
                }catch(eInner: SQLException) {
                    Napier.e("execSQLBatch: Exception running SQL: $sql")
                    throw eInner
                }
            }
            connection.commit()
        }catch(e: SQLException) {
            throw e
        }finally {
            statement?.close()
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
    fun createArrayOf(
        connection: Connection,
        arrayType: String,
        objects: kotlin.Array<out Any?>
    ) = connection.createArrayOf(jdbcDbType, arrayType, objects)

    companion object {
        const val DBINFO_TABLENAME = "_doorwayinfo"

    }

    override fun toString(): String {
        val name = when(this) {
            is DoorDatabaseRepository -> this.dbName
            is DoorDatabaseReplicateWrapper -> this.dbName
            is DoorDatabaseJdbc -> this.dbName
            else -> "Unknown"
        }
        return "${this::class.simpleName}: $name@${this.doorIdentityHashCode}"
    }
}