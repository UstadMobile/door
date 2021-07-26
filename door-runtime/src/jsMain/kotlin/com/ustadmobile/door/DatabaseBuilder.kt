package com.ustadmobile.door

import com.ustadmobile.door.ext.createInstance
import com.ustadmobile.door.migration.DoorMigration
import org.w3c.dom.Worker
import wrappers.SQLiteDatasourceJs
import kotlin.reflect.KClass

class DatabaseBuilder<T: DoorDatabase>(private var context: Any, private var dbClass: KClass<T>, private var dbName: String){

    private val callbacks = mutableListOf<DoorDatabaseCallback>()

    private val migrationList = mutableListOf<DoorMigration>()

    fun build(): T {
        val path = webWorkerPath
        if(path == null){
            throw Exception("Set WebWorker path before building your Database")
        }
        val implClass = implementationMap[dbClass] as KClass<T>
        val dataSource = SQLiteDatasourceJs(dbName, Worker(path))
        val dbImpl = implClass.js.createInstance(dataSource, false) as T
        //TODO: this needs to be reworked where the build function itself is suspended.
//        if(path != null){
//            dbImpl.init(dbName, path)
//        }
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

        private val implementationMap = mutableMapOf<KClass<*>, KClass<*>>()

        internal var webWorkerPath: String? = null

        fun <T> registerImplementation(dbClass: KClass<*>, implClass: KClass<*>) {
            implementationMap[dbClass] = implClass
        }

        fun <T : DoorDatabase> databaseBuilder(
            context: Any,
            dbClass: KClass<T>,
            dbName: String
        ): DatabaseBuilder<T> = DatabaseBuilder(context, dbClass, dbName)

    }
}