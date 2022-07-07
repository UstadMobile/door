package com.ustadmobile.door

import androidx.room.RoomDatabase
import com.ustadmobile.door.migration.DoorMigration

expect class DatabaseBuilder<T: RoomDatabase> {

    fun addMigrations(vararg migrations: DoorMigration): DatabaseBuilder<T>

    fun addCallback(callback: DoorDatabaseCallback) : DatabaseBuilder<T>
}