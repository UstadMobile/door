package com.ustadmobile.door

import com.ustadmobile.door.room.InvalidationTracker
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.attachments.AttachmentFilter
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.httpsql.HttpSqlDataSource
import com.ustadmobile.door.httpsql.HttpSqlDataSource.Companion.PROTOCOL_HTTPSQL_PREFIX
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.jdbc.ext.useStatementAsync
import com.ustadmobile.door.migration.DoorMigration
import com.ustadmobile.door.migration.DoorMigrationAsync
import com.ustadmobile.door.migration.DoorMigrationStatementList
import com.ustadmobile.door.migration.DoorMigrationSync
import com.ustadmobile.door.sqljsjdbc.*
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
        val dataSource = when {
            builderOptions.dbUrl.startsWith(PROTOCOL_SQLITE_PREFIX) && builderOptions is DatabaseBuilderOptionsSqliteJs -> {
                val indexeddbStoreName = builderOptions.dbUrl.substringAfter(PROTOCOL_SQLITE_PREFIX)
                SQLiteDatasourceJs(indexeddbStoreName, Worker(builderOptions.webWorkerPath))
            }
            builderOptions.dbUrl.startsWith(PROTOCOL_HTTPSQL_PREFIX) && builderOptions is DatabaseBuilderOptionsHttpSql-> {
                HttpSqlDataSource(builderOptions.dbUrl, builderOptions.httpClient, builderOptions.json)
            }
            else -> {
                throw IllegalArgumentException("Door/JS: Only SQLite or HTTPSQL is supported on JS! dbUrl must be in the " +
                        "form of sqlite:indexeddb_name or httpsql://")
            }
        }

        register(builderOptions.dbImplClasses)

        val dbImpl = builderOptions.dbImplClasses.dbImplKClass.js.createInstance(null, dataSource,
            builderOptions.dbUrl, listOf<AttachmentFilter>(), builderOptions.jdbcQueryTimeout, DoorDbType.SQLITE) as T

        val connection = dataSource.getConnectionAsyncOrFallback()
        val sqlDatabase = DoorSqlDatabaseConnectionImpl(connection)

        suspend fun Connection.execSqlAsync(vararg sqlStmts: String) {
            createStatement().useStatementAsync { stmt ->
                stmt.executeUpdateAsyncJs(sqlStmts.joinToString(separator = ";"))
            }
        }

        val dbMetaData = lookupImplementations(builderOptions.dbClass).metadata

        /*
         * When using HTTPSQL - the other side is responsible for migrations etc.
         */
        if(!builderOptions.dbUrl.startsWith(PROTOCOL_HTTPSQL_PREFIX)) {
            val exists = IndexedDb.checkIfExists(builderOptions.dbUrl.substringAfter(PROTOCOL_SQLITE_PREFIX))
            if(exists){
                Napier.d("DatabaseBuilderJs: database exists... loading\n", tag = DoorTag.LOG_TAG)
                (dataSource as SQLiteDatasourceJs).loadDbFromIndexedDb()
                var sqlCon = null as Connection?
                var stmt = null as PreparedStatement?
                var resultSet = null as ResultSet?

                var currentDbVersion = -1
                try {
                    sqlCon = dataSource.getConnectionAsyncOrFallback()
                    stmt = sqlCon.prepareStatementAsyncOrFallback("SELECT dbVersion FROM _doorwayinfo")
                    resultSet = stmt.executeQueryAsyncInt()
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
                Napier.i("DatabaseBuilderJs: Creating database ${builderOptions.dbUrl}\n")
                connection.execSqlAsync(*dbImpl.createAllTables().toTypedArray())
                Napier.d("DatabaseBuilderJs: Running onCreate callbacks...\n")
                callbacks.forEach {
                    when(it) {
                        is DoorDatabaseCallbackSync -> throw NotSupportedException("Cannot use sync callback on JS")
                        is DoorDatabaseCallbackStatementList -> {
                            Napier.d("DatabaseBuilderJs: Running onCreate callback: ${it::class.simpleName}")
                            connection.execSqlAsync(*it.onCreate(sqlDatabase).toTypedArray())
                        }
                    }
                }
            }

            Napier.d("DatabaseBuilderJs: Running onOpen callbacks...\n")
            //Run onOpen callbacks
            callbacks.forEach {
                when(it) {
                    is DoorDatabaseCallbackStatementList -> {
                        connection.execSqlAsync(*it.onOpen(sqlDatabase).toTypedArray())
                    }
                    else -> throw IllegalArgumentException("Cannot use sync callback on JS")
                }
            }

            Napier.d("DatabaseBuilderJs: Setting up trigger SQL\n")

            connection.execSqlAsync(
                *InvalidationTracker.generateCreateTriggersSql(dbMetaData.allTables, temporary = false).toTypedArray())

            Napier.d("DatabaseBuilderJs: Setting up change listener\n")

            if(builderOptions is DatabaseBuilderOptionsSqliteJs) {
                SaveToIndexedDbChangeListener(dbImpl, dataSource as SQLiteDatasourceJs, dbMetaData.replicateTableNames,
                    builderOptions.saveToIndexedDbDelayTime)
            }
        }


        connection.closeAsyncOrFallback()

        val dbWrappedIfNeeded = if(dbMetaData.hasReadOnlyWrapper) {
            dbImpl.wrap(builderOptions.dbClass)
        }else {
            dbImpl
        }

        Napier.d("Built ${builderOptions.dbUrl}\n")

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