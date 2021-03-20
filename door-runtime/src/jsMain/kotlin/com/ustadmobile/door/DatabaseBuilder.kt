package com.ustadmobile.door

import com.ustadmobile.door.ext.createInstance
import com.ustadmobile.door.ext.init
import kotlin.reflect.KClass

actual class DatabaseBuilder<T: DoorDatabase>(private var context: Any, private var dbClass: KClass<T>, private var dbName: String){

    actual fun addCallback(callback: DoorDatabaseCallback): DatabaseBuilder<T> {
        TODO("Not yet implemented")
    }

    actual fun addMigrations(vararg migrations: DoorMigration): DatabaseBuilder<T> {
        TODO("Not yet implemented")
    }

    actual fun build(): T {
        val path = webWorkerPath
        if(webWorkerPath == null){
            throw Exception("Set web worker path before building your Database")
        }
        val implClass = implementationMap[dbClass] as KClass<T>
        val dbImpl = implClass.js.createInstance(context, dbName) as T
        if(path != null){
            dbImpl.init(dbName, path)
        }
        return dbImpl
    }

    fun webWorker(path: String): DatabaseBuilder<T>{
        webWorkerPath = path
        return this
    }


    actual companion object {

        private val implementationMap = mutableMapOf<KClass<*>, KClass<*>>()

        internal var webWorkerPath: String? = null

        fun <T> registerImplementation(dbClass: KClass<*>, implClass: KClass<*>) {
            implementationMap[dbClass] = implClass
        }

        actual fun <T : DoorDatabase> databaseBuilder(
            context: Any,
            dbClass: KClass<T>,
            dbName: String
        ): DatabaseBuilder<T> = DatabaseBuilder(context, dbClass, dbName)

    }


}