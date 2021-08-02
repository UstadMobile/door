package com.ustadmobile.door

import com.ustadmobile.door.ext.createInstance
import com.ustadmobile.door.migration.DoorMigration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.Worker
import wrappers.DatabaseExportToIndexedDbCallback
import wrappers.IndexedDb
import wrappers.SQLiteDatasourceJs

class DatabaseBuilder<T: DoorDatabase>(private val builderOptions: DatabaseBuilderOptions,
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
                dbImpl.execSQLBatchAsync(dbImpl.createAllTables().joinToString(";"))
                callbacks.forEach { it.onCreate(dbImpl.sqlDatabaseImpl) }
            }else{
                TODO("Handling db builder with migrations")
            }
        }

        val changeListener = ChangeListenerRequest(dbImpl.getTableNamesAsync()){
            dbExportCallback.onExport(dataSource)
        }

        dbImpl.addChangeListener(changeListener)
        return dbImpl
    }

    fun webWorker(path: String): DatabaseBuilder<T>{
        webWorkerPath = path
        return this
    }

    fun addCallback(callback: DoorDatabaseCallback) : DatabaseBuilder<T>{
        callbacks.add(callback)
        return this
    }

    fun addMigrations(vararg migrations: DoorMigration): DatabaseBuilder<T> {
        migrationList.addAll(migrations)
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