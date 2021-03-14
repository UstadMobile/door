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
        val implClass = implementationMap[dbClass] as KClass<T>

        val dbImpl = implClass.js.createInstance(context, dbName) as T

        dbImpl.init(dbName)

        return dbImpl
    }


    actual companion object {

        private val implementationMap = mutableMapOf<KClass<*>, KClass<*>>()

        fun <T> registerImplementation(dbClass: KClass<*>, implClass: KClass<*>) {
            implementationMap[dbClass] = implClass
        }

        actual fun <T : DoorDatabase> databaseBuilder(
            context: Any,
            dbClass: KClass<T>,
            dbName: String
        ): DatabaseBuilder<T> {
            TODO("Not yet implemented")
        }

    }


}