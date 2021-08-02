package com.ustadmobile.door

import com.ustadmobile.door.ext.createInstance
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.migration.DoorMigration
import com.ustadmobile.door.migration.DoorMigrationAsync
import com.ustadmobile.door.migration.DoorMigrationStatementList
import com.ustadmobile.door.migration.DoorMigrationSync
import org.w3c.dom.Worker
import wrappers.*

actual class DatabaseBuilder<T: DoorDatabase>(private val builderOptions: DatabaseBuilderOptions,
                                              private val dbExportCallback: DatabaseExportToIndexedDbCallback){

    private val callbacks = mutableListOf<DoorDatabaseCallback>()

    private val migrationList = mutableListOf<DoorMigration>()

    suspend fun build(): T {
        val path = webWorkerPath ?: throw Exception("Set WebWorker path before building your Database")
        val dataSource = SQLiteDatasourceJs(builderOptions.dbName, Worker(path))
        val dbImpl = builderOptions.dbImplClass.js.createInstance(dataSource, false) as T
        val exists = IndexedDb.checkIfExists(builderOptions.dbName)
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
                    val nextMigration = migrationList.filter { it.startVersion == currentDbVersion}
                        .maxByOrNull { it.endVersion }
                    if(nextMigration != null) {
                        when(nextMigration) {
                            is DoorMigrationSync -> throw Exception("Synchronous Operation not supported!")
                            is DoorMigrationAsync -> nextMigration.migrateFn(dbImpl.sqlDatabaseImpl)
                            is DoorMigrationStatementList -> dbImpl.execSQLBatchAsync(*nextMigration.migrateStmts().toTypedArray())
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

        val changeListener = ChangeListenerRequest(dbImpl.getTableNamesAsync()){
            dbExportCallback.onExport(dataSource)
        }

        dbImpl.addChangeListener(changeListener)
        return dbImpl
    }

    actual fun addMigrations(vararg migrations: DoorMigration): DatabaseBuilder<T> {
        migrationList.addAll(migrations)
        return this
    }

    actual fun addCallback(callback: DoorDatabaseCallback): DatabaseBuilder<T> {
        callbacks.add(callback)
        return this
    }

    fun webWorker(path: String): DatabaseBuilder<T>{
        webWorkerPath = path
        return this
    }

    companion object {

        internal var webWorkerPath: String? = null

        fun <T : DoorDatabase> databaseBuilder(
            builderOptions: DatabaseBuilderOptions,
            dbExportCallback: DatabaseExportToIndexedDbCallback
        ): DatabaseBuilder<T> = DatabaseBuilder(builderOptions, dbExportCallback)

    }
}