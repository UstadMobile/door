package com.ustadmobile.door

import com.ustadmobile.door.ext.createInstance
import com.ustadmobile.door.migration.DoorMigration
import org.w3c.dom.Worker
import wrappers.IndexedDb
import wrappers.SQLiteDatasourceJs

class DatabaseBuilder<T: DoorDatabase>(private var context: Any, private val builderOptions: DatabaseBuilderOptions){

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
            dbImpl.execSQLBatchAsync(dbImpl.createAllTables().joinToString("#"))
        }
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
            context: Any,builderOptions: DatabaseBuilderOptions
        ): DatabaseBuilder<T> = DatabaseBuilder(context, builderOptions)

    }
}