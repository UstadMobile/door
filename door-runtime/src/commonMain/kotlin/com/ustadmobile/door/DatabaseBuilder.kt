package com.ustadmobile.door

import com.ustadmobile.door.migration.DoorMigration

expect class DatabaseBuilder<T: DoorDatabase> {

    fun addMigrations(vararg migrations: DoorMigration): DatabaseBuilder<T>

    fun addCallback(callback: DoorDatabaseCallback) : DatabaseBuilder<T>
}