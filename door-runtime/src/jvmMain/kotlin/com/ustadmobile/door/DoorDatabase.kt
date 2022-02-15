package com.ustadmobile.door


import com.ustadmobile.door.ext.*
import com.ustadmobile.door.jdbc.*
import com.ustadmobile.door.transaction.DoorTransactionDataSourceWrapper
import com.ustadmobile.door.util.SqliteChangeTracker
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.lang.reflect.Constructor
import kotlin.reflect.KClass

@Suppress("unused") //Some functions are used by generated code
actual abstract class DoorDatabase actual constructor(): DoorDatabaseCommon(){

    override var jdbcDbType: Int = -1
        get() = sourceDatabase?.jdbcDbType ?: field
        protected set

    @Volatile
    override var jdbcArraySupported: Boolean = false
        get() = sourceDatabase?.jdbcArraySupported ?: field
        protected set

    private val transactionMutex = Mutex()

    /**
     * This is true where this class is the actual JDBC implementation, false if it is a Repository or SyncReadOnlyWrapper etc
     */
    private val isImplementation: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        (this !is DoorDatabaseReplicateWrapper && this !is DoorDatabaseRepository)
    }

    private val constructorFun: Constructor<DoorDatabase> by lazy(LazyThreadSafetyMode.NONE) {
        @Suppress("UNCHECKED_CAST")
        this::class.java.getConstructor(DoorDatabase::class.java, DataSource::class.java,
            String::class.java, File::class.java, List::class.java, Int::class.javaPrimitiveType) as Constructor<DoorDatabase>
    }

    override val tableNames: List<String> by lazy {
        val delegatedDatabaseVal = sourceDatabase
        if(delegatedDatabaseVal != null) {
            delegatedDatabaseVal.tableNames
        }else {
            val thisJdbc = this as DoorDatabaseJdbc

            //Use the dataSource directly because this is called before the
            // tables are created
            thisJdbc.dataSource.connection.use { con ->
                val metadata = con.metaData
                metadata.getTables(null, null, "%", arrayOf("TABLE")).useResults { tableResult ->
                    tableResult.mapRows { it.getString("TABLE_NAME") ?: "" }
                }
            }
        }

    }

    protected fun setupFromDataSource() {
        val rootDb = rootDatabase
        if(this == rootDb) {
            val jdbcDb = (this as DoorDatabaseJdbc)
            jdbcDb.dataSource.connection.use { connection ->
                jdbcDbType = DoorDbType.typeIntFromProductName(connection.metaData?.databaseProductName ?: "")
                jdbcArraySupported = jdbcDbType == DoorDbType.POSTGRES
            }
        }else {
            jdbcDbType = rootDb.jdbcDbType
            jdbcArraySupported = rootDb.jdbcArraySupported
        }
    }

    /**
     * Creates a new JDBC Database implementation for use in a transaction. It will use the root database to get a
     * connection, then set auto commit to false, and create a new jdbc database using DoorTransactionDataSourceWrapper
     */
    private inline fun <R> useTransactionDb(
        block: (transactionDb: DoorDatabase) -> R
    ) : R {
        return try {
            val rootDb = rootDatabase
            val rootJdbcDb = (rootDb as DoorDatabaseJdbc)
            val connection = rootJdbcDb.dataSource.connection
            connection.autoCommit = false

            val changedTables = mutableSetOf<String>()
            DoorTransactionDataSourceWrapper(rootDb, connection).use { transactionDataSource ->
                val transactionDb = rootDb.constructorFun.newInstance(rootDb, transactionDataSource,
                    "Transaction wrapper for $rootDb", rootDb.realAttachmentStorageUri?.toFile(),
                    rootJdbcDb.realAttachmentFilters, rootJdbcDb.jdbcQueryTimeout)
                (transactionDb as DoorDatabaseJdbc).transactionDepthCounter.incrementTransactionDepth()

                lateinit var sqliteChangeTracker: SqliteChangeTracker
                if(dbType() == DoorDbType.SQLITE) {
                    sqliteChangeTracker = SqliteChangeTracker(this@DoorDatabase::class.doorDatabaseMetadata())
                    sqliteChangeTracker.setupTriggersOnConnection(connection)
                }

                block(transactionDb).also {
                    if(dbType() == DoorDbType.SQLITE)
                        changedTables.addAll(sqliteChangeTracker.findChangedTablesOnConnection(connection))
                }
            }.also {
                rootJdbcDb.invalidationTracker.takeIf { changedTables.isNotEmpty() }?.onTablesInvalidated(changedTables)
            }
        }catch(e: Exception) {
            Napier.e("Exception in useTransactionDataSourceAndDb", tag = DoorTag.LOG_TAG)
            throw e
        }
    }

    /**
     * SQLite works in a one-at-a-time mode with a timeout. Hopefully this will not be needed after updating to use
     * triggers for change detection.
     */
    private suspend inline fun <T> withTransactionMutexIfSqlite(block: () -> T): T {
        return if(dbType() == DoorDbType.SQLITE) {
            rootDatabase.transactionMutex.withLock(action = block)
        }else {
            block()
        }
    }

    private fun KClass<*>.assertIsClassForThisDb() {
        if(!this.java.isAssignableFrom(this@DoorDatabase::class.java))
            throw IllegalArgumentException("withDoorTransactionInternal wrong class param!")
    }

    /**
     * When a new transaction is started, a new database implementation object will be instantiated using the
     * TransactionDataSourceWrapper. If the user is actually interacting with a ReadOnlyWrapper or Repository, then
     * the new database implementation object itself should be wrapped as the same type (e.g. readonlywrapper, repo,
     * etc).
     *
     * This function is overridden by generated code for ReadOnlyWrappers and Repositories
     *
     * @param dbKClass the KClass representing the database itself (the abstract db class e.g. MyDatabase::class ,
     *                 not any generated implementation
     * @param transactionDb the database object to wrap (e.g. as a ReadOnlyWrapper or Repository)
     * @return transactionDb wrapped
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun <T: DoorDatabase> wrapForNewTransaction(dbKClass: KClass<T>, transactionDb: T) : T {
        return transactionDb
    }

    /**
     * Unfortunately, this can't really be internal because it is overriden in generated code
     */
    @Suppress("UNCHECKED_CAST")
    internal fun <T: DoorDatabase, R> withDoorTransactionInternal(
        block: (T) -> R
    ): R {
        val txRoot = transactionRootJdbcDb
        return if(!txRoot.isInTransaction) {
            runBlocking {
                withTransactionMutexIfSqlite {
                    useTransactionDb { transactionDb ->
                        val dbKClassInt: KClass<T> = this@DoorDatabase::class.doorDatabaseMetadata().dbClass as KClass<T>
                        val transactionWrappedDb = wrapForNewTransaction(dbKClassInt, transactionDb as T)
                        block(transactionWrappedDb)
                    }
                }
            }
        }else {
            try {
                txRoot.transactionDepthCounter.incrementTransactionDepth()
                block(this as T)
            }finally {
                txRoot.transactionDepthCounter.decrementTransactionDepth()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal suspend fun <T: DoorDatabase, R> withDoorTransactionInternalAsync(
        block: suspend (T) -> R
    ): R {
        val txRoot = transactionRootJdbcDb
        return if(!txRoot.isInTransaction) {
            withTransactionMutexIfSqlite {
                useTransactionDb { transactionDb ->
                    val dbKClassInt: KClass<T> = this@DoorDatabase::class.doorDatabaseMetadata().dbClass as KClass<T>
                    val transactionWrappedDb = wrapForNewTransaction(dbKClassInt, transactionDb as T)
                    block(transactionWrappedDb)
                }
            }
        }else {
            try {
                txRoot.transactionDepthCounter.incrementTransactionDepth()
                block(this as T)
            }finally {
                txRoot.transactionDepthCounter.decrementTransactionDepth()
            }
        }
    }

    actual override fun runInTransaction(runnable: Runnable) {
        super.runInTransaction(runnable)
    }

    actual abstract fun clearAllTables()

}