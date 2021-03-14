package com.ustadmobile.door

import kotlin.reflect.KClass

actual class DatabaseBuilder<T : DoorDatabase> {

    actual fun addCallback(callback: DoorDatabaseCallback): DatabaseBuilder<T> {
        TODO("Not yet implemented")
    }

    actual fun addMigrations(vararg migrations: DoorMigration): DatabaseBuilder<T> {
        TODO("Not yet implemented")
    }

    actual fun build(): T {
        TODO("Not yet implemented")
    }

    actual companion object {
        actual fun <T : DoorDatabase> databaseBuilder(
            context: Any,
            dbClass: KClass<T>,
            dbName: String
        ): DatabaseBuilder<T> {
            TODO("Not yet implemented")
        }

    }

}