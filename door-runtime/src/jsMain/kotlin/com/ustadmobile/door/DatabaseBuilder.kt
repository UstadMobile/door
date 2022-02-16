package com.ustadmobile.door

import com.ustadmobile.door.attachments.AttachmentFilter
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.createInstance
import com.ustadmobile.door.ext.wrap
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.migration.DoorMigration
import com.ustadmobile.door.migration.DoorMigrationAsync
import com.ustadmobile.door.migration.DoorMigrationStatementList
import com.ustadmobile.door.migration.DoorMigrationSync
import com.ustadmobile.door.sqljsjdbc.*
import com.ustadmobile.door.util.DoorJsImplClasses
import com.ustadmobile.door.util.SqliteChangeTracker
import io.github.aakira.napier.Napier
import org.w3c.dom.Worker
import kotlin.reflect.KClass

actual class DatabaseBuilder<T: DoorDatabase> private constructor(
    private val builderOptions: DatabaseBuilderOptions<T>
) {

    private val callbacks = mutableListOf<DoorDatabaseCallback>()

    private val migrationList = mutableListOf<DoorMigration>()

    suspend fun build(): T {
        val dataSource = SQLiteDatasourceJs(builderOptions.dbName, Worker(builderOptions.webWorkerPath))
        register(builderOptions.dbImplClasses)

        val dbImpl = builderOptions.dbImplClasses.dbImplKClass.js.createInstance(null, dataSource,
            builderOptions.dbName, listOf<AttachmentFilter>(), builderOptions.jdbcQueryTimeout) as T
        val exists = IndexedDb.checkIfExists(builderOptions.dbName)

        if(exists){
            Napier.d("DatabaseBuilderJs: database exists... loading", tag = DoorTag.LOG_TAG)
            dataSource.loadDbFromIndexedDb()
            var sqlCon = null as SQLiteConnectionJs?
            var stmt = null as SQLitePreparedStatementJs?
            var resultSet = null as SQLiteResultSet?

            var currentDbVersion = -1
            try {
                sqlCon = dataSource.getConnection() as SQLiteConnectionJs
                stmt = SQLitePreparedStatementJs(sqlCon,"SELECT dbVersion FROM _doorwayinfo")
                resultSet = stmt.executeQueryAsyncInt() as SQLiteResultSet
                if(resultSet.next())
                    currentDbVersion = resultSet.getInt(1)
            }catch(exception: SQLException) {
                throw exception
            }finally {
                resultSet?.close()
                stmt?.close()
                sqlCon?.close()
            }

            Napier.d("DatabaseBuilderJs: Found current db version = $currentDbVersion", tag = DoorTag.LOG_TAG)
            while(currentDbVersion < dbImpl.dbVersion) {
                val nextMigration = migrationList.filter {
                    it.startVersion == currentDbVersion && it !is DoorMigrationSync
                }.maxByOrNull { it.endVersion }

                if(nextMigration != null) {
                    Napier.d("DatabaseBuilderJs: Attempting to upgrade from ${nextMigration.startVersion} to " +
                            "${nextMigration.endVersion}", tag = DoorTag.LOG_TAG)
                    when(nextMigration) {
                        is DoorMigrationAsync -> nextMigration.migrateFn(dbImpl.sqlDatabaseImpl)
                        is DoorMigrationStatementList -> dbImpl.execSQLBatchAsyncJs(
                            *nextMigration.migrateStmts(dbImpl.sqlDatabaseImpl).toTypedArray())
                        else -> throw IllegalArgumentException("Cannot use DataMigrationSync on JS")
                    }

                    currentDbVersion = nextMigration.endVersion
                    dbImpl.execSQLBatchAsyncJs("UPDATE _doorwayinfo SET dbVersion = $currentDbVersion")
                    Napier.d("DatabaseBuilderJs: migrated up to $currentDbVersion", tag = DoorTag.LOG_TAG)
                }else {
                    throw IllegalStateException("Need to migrate to version " +
                            "${dbImpl.dbVersion} from $currentDbVersion - could not find next migration")
                }
            }
        }else{
            if(!dbImpl.getTableNamesAsync().any {it.lowercase() == DoorDatabaseCommon.DBINFO_TABLENAME}) {
                Napier.i("DatabaseBuilderJs: Creating database ${builderOptions.dbName}")
                dbImpl.execSQLBatchAsyncJs(*dbImpl.createAllTables().toTypedArray())
                Napier.d("DatabaseBuilderJs: Running onCreate callbacks...")
                callbacks.forEach {
                    when(it) {
                        is DoorDatabaseCallbackSync -> throw NotSupportedException("Cannot use sync callback on JS")
                        is DoorDatabaseCallbackStatementList -> {
                            Napier.d("DatabaseBuilderJs: Running onCreate callback: ${it::class.simpleName}")
                            dbImpl.execSQLBatchAsyncJs(*it.onCreate(dbImpl.sqlDatabaseImpl).toTypedArray())
                        }
                    }
                }
            }
        }

        Napier.d("DatabaseBuilderJs: Running onOpen callbacks...")
        //Run onOpen callbacks
        callbacks.forEach {
            when(it) {
                is DoorDatabaseCallbackStatementList -> {
                    dbImpl.execSQLBatchAsyncJs(*it.onOpen(dbImpl.sqlDatabaseImpl).toTypedArray())
                }
                else -> throw IllegalArgumentException("Cannot use sync callback on JS")
            }
        }

        //Put the temp tables and triggers in place.
        SqliteChangeTracker(builderOptions.dbImplClasses.metadata).setupTriggersOnDbAsync(
            dbImpl, temporary = false)
        (dbImpl as DoorDatabaseJdbc).invalidationTracker.active = true

        val dbMetaData = lookupImplementations(builderOptions.dbClass).metadata
        SaveToIndexedDbChangeListener(dbImpl, dataSource, dbMetaData.replicateTableNames,
            builderOptions.saveToIndexedDbDelayTime)
        return if(dbMetaData.hasReadOnlyWrapper) {
            dbImpl.wrap(builderOptions.dbClass)
        }else {
            dbImpl
        }
    }

    actual fun addMigrations(vararg migrations: DoorMigration): DatabaseBuilder<T> {
        migrationList.addAll(migrations)
        return this
    }

    actual fun addCallback(callback: DoorDatabaseCallback): DatabaseBuilder<T> {
        Napier.d("DatabaseBuilderJs: Add Callback: ${callback::class.simpleName}", tag = DoorTag.LOG_TAG)
        callbacks.add(callback)
        return this
    }

    fun queryTimeout(seconds: Int) {
        builderOptions.jdbcQueryTimeout = seconds
    }

    companion object {

        private val implementationMap = mutableMapOf<KClass<*>, DoorJsImplClasses<*>>()

        fun <T : DoorDatabase> databaseBuilder(
            builderOptions: DatabaseBuilderOptions<T>
        ): DatabaseBuilder<T> = DatabaseBuilder(builderOptions)

        @Suppress("UNCHECKED_CAST")
        fun <T: DoorDatabase> lookupImplementations(dbKClass: KClass<T>): DoorJsImplClasses<T> {
            return implementationMap[dbKClass] as? DoorJsImplClasses<T>
                ?: throw IllegalArgumentException("${dbKClass.simpleName} is not registered through DatabaseBuilder.register")
        }

        internal fun register(implClasses: DoorJsImplClasses<*>) {
            implementationMap[implClasses.dbKClass] = implClasses
            implementationMap[implClasses.dbImplKClass] = implClasses
            implClasses.repositoryImplClass?.also {
                implementationMap[it] = implClasses
            }
            implClasses.replicateWrapperImplClass?.also {
                implementationMap[it]  =implClasses
            }
        }

    }
}