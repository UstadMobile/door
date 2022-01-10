package com.ustadmobile.door


import com.ustadmobile.door.ext.*
import com.ustadmobile.door.jdbc.*
import com.ustadmobile.door.transaction.DoorTransactionDataSourceWrapper
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.atomic
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

    /**
     * This is true where this class is the actual JDBC implementation, false if it is a Repository or SyncReadOnlyWrapper etc
     */
    private val isImplementation: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        (this !is DoorDatabaseReplicateWrapper && this !is DoorDatabaseRepository)
    }

    private val constructorFun: Constructor<DoorDatabase> by lazy(LazyThreadSafetyMode.NONE) {
        @Suppress("UNCHECKED_CAST")
        this::class.java.getConstructor(DoorDatabase::class.java, DataSource::class.java, String::class.java) as Constructor<DoorDatabase>
    }

    override val tableNames: List<String> by lazy {
        val delegatedDatabaseVal = sourceDatabase
        if(delegatedDatabaseVal != null) {
            delegatedDatabaseVal.tableNames
        }else {
            val thisJdbc = this as DoorDatabaseJdbc
            var con = null as Connection?
            val tableNamesList = mutableListOf<String>()
            var tableResult: ResultSet? = null
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
                tableResult?.close()
                con?.close()
            }

            tableNamesList.toList()
        }

    }

    protected fun setupFromDataSource() {
        val rootDb = rootDatabase
        if(this == rootDb) {
            val jdbcDb = (this as DoorDatabaseJdbc)
            jdbcDb.openConnection().use { dbConnection ->
                jdbcDbType = DoorDbType.typeIntFromProductName(dbConnection.metaData?.databaseProductName ?: "")
                jdbcArraySupported = jdbcDbType == DoorDbType.POSTGRES
            }
        }else {
            jdbcDbType = rootDb.jdbcDbType
            jdbcArraySupported = rootDb.jdbcArraySupported
        }
    }

    private fun createTransactionDataSourceAndDb(): Pair<DoorTransactionDataSourceWrapper, DoorDatabase> {
        val rootDb = rootDatabase
        val connection = (rootDb as DoorDatabaseJdbc).openConnection()
        connection.setAutoCommit(false)
        val transactionDataSource = DoorTransactionDataSourceWrapper(rootDb, connection)
        val transactionDb = rootDb.constructorFun.newInstance(rootDb, transactionDataSource,
                "Transaction wrapper for $rootDb")

        (transactionDb as DoorDatabaseJdbc).transactionDepthCounter.incrementTransactionDepth()

        return Pair(transactionDataSource, transactionDb)
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
    open fun <T: DoorDatabase, R> withDoorTransactionInternal(
        dbKClass: KClass<T>,
        block: (T) -> R
    ): R {
        dbKClass.assertIsClassForThisDb()

        val txRoot = transactionRootJdbcDb
        return if(!txRoot.isInTransaction) {
            val (transactionDs, transactionDb) = createTransactionDataSourceAndDb()
            transactionDs.use {
                val transactionWrappedDb = wrapForNewTransaction(dbKClass, transactionDb as T)
                val result = block(transactionWrappedDb)
                result
            }.also {
                transactionDb.transactionRootJdbcDb.invalidationTracker.onCommit()
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
    internal open suspend fun <T: DoorDatabase, R> withDoorTransactionInternalAsync(
        dbKClass: KClass<T>,
        block: suspend (T) -> R
    ): R {
        dbKClass.assertIsClassForThisDb()
        val txRoot = transactionRootJdbcDb
        return if(!txRoot.isInTransaction) {
            val (transactionDs, transactionDb) = createTransactionDataSourceAndDb()
            transactionDs.useAsync {
                val transactionWrappedDb = wrapForNewTransaction(dbKClass, transactionDb as T)
                val result = block(transactionWrappedDb)
                result
            }.also {
                transactionDb.transactionRootJdbcDb.invalidationTracker.onCommit()
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