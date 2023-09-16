package com.ustadmobile.door

import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.createInstance
import com.ustadmobile.door.ext.wrap
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.jdbc.ext.useStatementAsync
import com.ustadmobile.door.migration.DoorMigration
import com.ustadmobile.door.migration.DoorMigrationAsync
import com.ustadmobile.door.migration.DoorMigrationStatementList
import com.ustadmobile.door.migration.DoorMigrationSync
import com.ustadmobile.door.room.InvalidationTracker
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.sqljsjdbc.*
import com.ustadmobile.door.sqljsjdbc.SQLiteDatasourceJs.Companion.LOCATION_MEMORY
import com.ustadmobile.door.sqljsjdbc.SQLiteDatasourceJs.Companion.PROTOCOL_SQLITE_PREFIX
import com.ustadmobile.door.util.DoorJsImplClasses
import io.github.aakira.napier.Napier
import org.w3c.dom.Worker
import kotlin.reflect.KClass

class DatabaseBuilder<T: RoomDatabase> private constructor(
    private val builderOptions: DatabaseBuilderOptions<T>
) {

    private val callbacks = mutableListOf<DoorDatabaseCallback>()

    private val migrationList = mutableListOf<DoorMigration>()

    suspend fun build(): T {
        if(!builderOptions.dbUrl.startsWith(PROTOCOL_SQLITE_PREFIX))
            throw IllegalArgumentException("Door/JS: Only SQLite is supported on JS! dbUrl must be in the form of " +
                    "sqlite::memory: OR sqlite:indexeddb_name")

        val storageLocation = builderOptions.dbUrl.substringAfter(PROTOCOL_SQLITE_PREFIX)
        val dataSource = SQLiteDatasourceJs(storageLocation, Worker(builderOptions.webWorkerPath))
        register(builderOptions.dbImplClasses)

        val dbImpl = builderOptions.dbImplClasses.dbImplKClass.js.createInstance(null, dataSource,
            builderOptions.dbUrl, builderOptions.jdbcQueryTimeout, DoorDbType.SQLITE) as T
        val loadFromIndexedDb = storageLocation != LOCATION_MEMORY &&
                IndexedDb.checkIfExists(storageLocation)
        val connection = dataSource.getConnection()
        val sqlDatabase = DoorSqlDatabaseConnectionImpl(connection, DoorDbType.SQLITE)

        suspend fun Connection.execSqlAsync(vararg sqlStmts: String) {
            createStatement().useStatementAsync { stmt ->
                stmt.executeUpdateAsyncJs(sqlStmts.joinToString(separator = ";"))
            }
        }


        if(loadFromIndexedDb){
            Napier.i("DatabaseBuilderJs: database ${builderOptions.dbUrl} exists... loading\n", tag = DoorTag.LOG_TAG)
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

            Napier.d("DatabaseBuilderJs: Found current db version = $currentDbVersion\n", tag = DoorTag.LOG_TAG)
            while(currentDbVersion < dbImpl.dbVersion) {
                val nextMigration = migrationList.filter {
                    it.startVersion == currentDbVersion && it !is DoorMigrationSync
                }.maxByOrNull { it.endVersion }

                if(nextMigration != null) {
                    Napier.d("DatabaseBuilderJs: Attempting to upgrade from ${nextMigration.startVersion} to " +
                            "${nextMigration.endVersion}\n", tag = DoorTag.LOG_TAG)
                    when(nextMigration) {
                        is DoorMigrationAsync -> nextMigration.migrateFn(sqlDatabase)
                        is DoorMigrationStatementList -> connection.execSqlAsync(
                            *nextMigration.migrateStmts(sqlDatabase).toTypedArray())
                        else -> throw IllegalArgumentException("Cannot use DataMigrationSync on JS")
                    }

                    currentDbVersion = nextMigration.endVersion
                    connection.execSqlAsync("UPDATE _doorwayinfo SET dbVersion = $currentDbVersion")
                    Napier.d("DatabaseBuilderJs: migrated up to $currentDbVersion", tag = DoorTag.LOG_TAG)
                }else {
                    throw IllegalStateException("Need to migrate to version " +
                            "${dbImpl.dbVersion} from $currentDbVersion - could not find next migration")
                }
            }
        }else{
            Napier.i("DatabaseBuilderJs: Creating database ${builderOptions.dbUrl}\n", tag = DoorTag.LOG_TAG)
            connection.execSqlAsync(*dbImpl.createAllTables().toTypedArray())
            Napier.d("DatabaseBuilderJs: Running onCreate callbacks...\n", tag = DoorTag.LOG_TAG)
            callbacks.forEach {
                when(it) {
                    is DoorDatabaseCallbackSync -> throw NotSupportedException("Cannot use sync callback on JS")
                    is DoorDatabaseCallbackStatementList -> {
                        Napier.d("DatabaseBuilderJs: Running onCreate callback: ${it::class.simpleName}",
                            tag = DoorTag.LOG_TAG)
                        connection.execSqlAsync(*it.onCreate(sqlDatabase).toTypedArray())
                    }
                }
            }
        }

        Napier.d("DatabaseBuilderJs: Running onOpen callbacks...\n", tag = DoorTag.LOG_TAG)
        //Run onOpen callbacks
        callbacks.forEach {
            when(it) {
                is DoorDatabaseCallbackStatementList -> {
                    connection.execSqlAsync(*it.onOpen(sqlDatabase).toTypedArray())
                }
                else -> throw IllegalArgumentException("Cannot use sync callback on JS")
            }
        }

        Napier.d("DatabaseBuilderJs: Setting up trigger SQL\n", tag = DoorTag.LOG_TAG)
        val dbMetaData = lookupImplementations(builderOptions.dbClass).metadata
        connection.execSqlAsync(
            *InvalidationTracker.generateCreateTriggersSql(dbMetaData.allTables, temporary = false).toTypedArray())

        Napier.d("DatabaseBuilderJs: Setting up change listener\n", tag = DoorTag.LOG_TAG)

        if(storageLocation != LOCATION_MEMORY)  {
            SaveToIndexedDbChangeListener(dbImpl, dataSource, dbMetaData.replicateTableNames,
                builderOptions.saveToIndexedDbDelayTime)
        }

        connection.close()

        val dbWrappedIfNeeded = if(dbMetaData.hasReadOnlyWrapper) {
            dbImpl.wrap(builderOptions.dbClass, 0)
        }else {
            dbImpl
        }

        Napier.i("Built database for: ${builderOptions.dbUrl}\n", tag = DoorTag.LOG_TAG)

        return dbWrappedIfNeeded
    }

    fun addMigrations(vararg migrations: DoorMigration): DatabaseBuilder<T> {
        migrationList.addAll(migrations)
        return this
    }

    fun addCallback(callback: DoorDatabaseCallback): DatabaseBuilder<T> {
        Napier.d("DatabaseBuilderJs: Add Callback: ${callback::class.simpleName}", tag = DoorTag.LOG_TAG)
        callbacks.add(callback)
        return this
    }

    fun queryTimeout(seconds: Int) {
        builderOptions.jdbcQueryTimeout = seconds
    }

    companion object {

        private val implementationMap = mutableMapOf<KClass<*>, DoorJsImplClasses<*>>()

        fun <T : RoomDatabase> databaseBuilder(
            builderOptions: DatabaseBuilderOptions<T>
        ): DatabaseBuilder<T> = DatabaseBuilder(builderOptions)

        @Suppress("UNCHECKED_CAST")
        fun <T: RoomDatabase> lookupImplementations(dbKClass: KClass<T>): DoorJsImplClasses<T> {
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