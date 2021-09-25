package com.ustadmobile.door


import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.jdbc.*
import com.ustadmobile.door.transaction.DoorTransactionDataSourceWrapper
import kotlinx.atomicfu.atomic
import java.lang.reflect.Constructor
import kotlin.reflect.KClass

@Suppress("unused") //Some functions are used by generated code
actual abstract class DoorDatabase actual constructor() : DoorDatabaseCommon(){

    override var jdbcDbType: Int = -1
        get() = sourceDatabase?.jdbcDbType ?: field
        protected set

    @Volatile
    override var jdbcArraySupported: Boolean = false
        get() = sourceDatabase?.jdbcArraySupported ?: field
        protected set

    private val transactionDepth = atomic(0)

    private val transactionTablesChanged = concurrentSafeMapOf<String, String>()

    /**
     * This is true where this class is the actual JDBC implementation, false if it is a Repository or SyncReadOnlyWrapper etc
     */
    private val isImplementation: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        (this !is DoorDatabaseSyncableReadOnlyWrapper && this !is DoorDatabaseRepository)
    }

    private val constructorFun: Constructor<DoorDatabase> by lazy(LazyThreadSafetyMode.NONE) {
        if(this is SyncableDoorDatabase) {
            @Suppress("UNCHECKED_CAST")
            this::class.java.getConstructor(DataSource::class.java, Boolean::class.javaPrimitiveType) as Constructor<DoorDatabase>
        }else {
            @Suppress("UNCHECKED_CAST")
            this::class.java.getConstructor(DataSource::class.java) as Constructor<DoorDatabase>
        }
    }

    override val tableNames: List<String> by lazy {
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

    protected fun setupFromDataSource() {
        openConnection().use { dbConnection ->
            jdbcDbType = DoorDbType.typeIntFromProductName(dbConnection.metaData?.databaseProductName ?: "")
            jdbcArraySupported = jdbcDbType == DoorDbType.POSTGRES
        }
    }

    override fun openConnection(): java.sql.Connection {
        return if(transactionDepth.value == 0)
            super.openConnection()
        else
            dataSource.connection
    }

    private fun createTransactionDataSourceAndDb(): Pair<DoorTransactionDataSourceWrapper, DoorDatabase> {
        val transactionDataSource = DoorTransactionDataSourceWrapper(effectiveDatabase.dataSource)
        val transactionDb = if(this is SyncableDoorDatabase) {
            constructorFun.newInstance(transactionDataSource, false)
        }else {
            constructorFun.newInstance(transactionDataSource)
        }

        transactionDb.sourceDatabase = this
        transactionDb.transactionDepth.value = 1

        return Pair(transactionDataSource, transactionDb)
    }

    private fun KClass<*>.assertIsClassForThisDb() {
        if(!this.java.isAssignableFrom(this@DoorDatabase::class.java))
            throw IllegalArgumentException("withDoorTransactionInternal wrong class param!")
    }

    /**
     * This is overriden by SyncReadOnlyWrappers and Repositories
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun <T: DoorDatabase> wrapNewTransaction(dbKClass: KClass<T>, transactionDb: T) : T {
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

        return when(transactionDepth.value) {
            0 -> {
                val (transactionDs, transactionDb) = createTransactionDataSourceAndDb()
                transactionDs.use {
                    val result = block(wrapNewTransaction(dbKClass, transactionDb as T))
                    transactionDb.fireTransactionTablesChanged()
                    result
                }
            }

            else -> {
                try {
                    transactionDepth.incrementAndGet()
                    block(this as T)
                }finally {
                    transactionDepth.decrementAndGet()
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal open suspend fun <T: DoorDatabase, R> withDoorTransactionInternalAsync(
        dbKClass: KClass<T>,
        block: suspend (T) -> R
    ): R {
        dbKClass.assertIsClassForThisDb()

        return when(transactionDepth.value) {
            0 -> {
                val (transactionDs, transactionDb) = createTransactionDataSourceAndDb()
                transactionDs.useAsync {
                    val result = block(wrapNewTransaction(dbKClass, transactionDb as T))
                    transactionDb.fireTransactionTablesChanged()
                    result
                }
            }

            else -> {
                try {
                    transactionDepth.incrementAndGet()
                    block(this as T)
                }finally {
                    transactionDepth.decrementAndGet()
                }
            }
        }
    }

    override fun handleTableChanged(changeTableNames: List<String>): DoorDatabase {
        //If this a transaction, then changes need to be collected together and only fired once the transaction is
        //complete.
        if(!isImplementation || transactionDepth.value == 0) {
            super.handleTableChanged(changeTableNames)
        }else {
            transactionTablesChanged.putAll(changeTableNames.associateWith { key -> key })
        }

        return this
    }

    internal fun fireTransactionTablesChanged() {
        super.handleTableChanged(transactionTablesChanged.values.toList())
        transactionTablesChanged.clear()
    }

    actual override fun runInTransaction(runnable: Runnable) {
        super.runInTransaction(runnable)
    }

    actual abstract fun clearAllTables()

}