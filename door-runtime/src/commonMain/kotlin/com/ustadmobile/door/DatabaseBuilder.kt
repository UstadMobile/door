package com.ustadmobile.door

import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

expect class DatabaseBuilder<T: DoorDatabase> {

    companion object {

        @JvmStatic
        fun <T : DoorDatabase> databaseBuilder(context: Any, dbClass: KClass<T>, dbName: String): DatabaseBuilder<T>

    }

    fun addCallback(callback: DoorDatabaseCallback): DatabaseBuilder<T>

    fun addMigrations(vararg migrations: DoorMigration): DatabaseBuilder<T>

    fun build() : T


}