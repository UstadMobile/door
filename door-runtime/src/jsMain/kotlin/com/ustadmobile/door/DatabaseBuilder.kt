package com.ustadmobile.door

import com.ustadmobile.door.attachments.AttachmentFilter
import com.ustadmobile.door.ext.createInstance
import com.ustadmobile.door.ext.wrap
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.migration.DoorMigration
import com.ustadmobile.door.migration.DoorMigrationAsync
import com.ustadmobile.door.migration.DoorMigrationStatementList
import com.ustadmobile.door.migration.DoorMigrationSync
import com.ustadmobile.door.sqljsjdbc.*
import com.ustadmobile.door.util.DoorJsImplClasses
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

        val dbImpl = builderOptions.dbImplClasses.dbImplKClass.js.createInstance(null, dataSource, false,
            listOf<AttachmentFilter>()) as T
        val exists = IndexedDb.checkIfExists(builderOptions.dbName)
        SaveToIndexedDbChangeListener(dbImpl, dataSource, builderOptions.saveToIndexedDbDelayTime)
        if(exists){
            dataSource.loadDbFromIndexedDb()
        }else{
            if(!dbImpl.getTableNamesAsync().any {it.lowercase() == DoorDatabaseCommon.DBINFO_TABLENAME}) {
                dbImpl.execSQLBatchAsync(*dbImpl.createAllTables().toTypedArray())
                callbacks.forEach { it.onCreate(dbImpl.sqlDatabaseImpl) }
            }else{
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

                while(currentDbVersion < dbImpl.dbVersion) {
                    val nextMigration = migrationList.filter {
                        it.startVersion == currentDbVersion && it !is DoorMigrationSync
                    }
                    .maxByOrNull { it.endVersion }

                    if(nextMigration != null) {
                        when(nextMigration) {
                            is DoorMigrationAsync -> nextMigration.migrateFn(dbImpl.sqlDatabaseImpl)
                            is DoorMigrationStatementList -> dbImpl.execSQLBatchAsync(
                                *nextMigration.migrateStmts(dbImpl.sqlDatabaseImpl).toTypedArray())
                        }

                        currentDbVersion = nextMigration.endVersion
                        dbImpl.execSQLBatchAsync("UPDATE _doorwayinfo SET dbVersion = $currentDbVersion")
                    }else {
                        throw IllegalStateException("Need to migrate to version " +
                                "${dbImpl.dbVersion} from $currentDbVersion - could not find next migration")
                    }
                }
            }
        }

        val dbMetaData = lookupImplementations(builderOptions.dbClass).metadata
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
        callbacks.add(callback)
        return this
    }

    companion object {

        private val implementationMap = mutableMapOf<KClass<*>, DoorJsImplClasses<*>>()

        fun <T : DoorDatabase> databaseBuilder(
            builderOptions: DatabaseBuilderOptions<T>
        ): DatabaseBuilder<T> = DatabaseBuilder(builderOptions)

        internal fun <T: DoorDatabase> lookupImplementations(dbKClass: KClass<T>): DoorJsImplClasses<T> {
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