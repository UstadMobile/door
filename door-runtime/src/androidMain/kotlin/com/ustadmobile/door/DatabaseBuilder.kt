package com.ustadmobile.door

import android.content.Context
import androidx.room.Room
import kotlin.reflect.KClass
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("UNCHECKED_CAST")
actual class DatabaseBuilder<T: DoorDatabase>(private val roomBuilder: RoomDatabase.Builder<T>,
                                              val dbClass: KClass<T>) {

    actual companion object {
        actual fun <T : DoorDatabase> databaseBuilder(context: Any, dbClass: KClass<T>, dbName: String): DatabaseBuilder<T> {
            val applicationContext = (context as Context).applicationContext
            val builder = DatabaseBuilder(Room.databaseBuilder(applicationContext, dbClass.java, dbName),
                dbClass)

            val callbackClassName = "${dbClass.java.canonicalName}_SyncCallback"
            println("Attempt to load callback $callbackClassName")

            val callbackClass = Class.forName(callbackClassName).newInstance() as DoorDatabaseCallback

            builder.addCallback(callbackClass)

            return builder
        }
    }



    actual fun build(): T {
        val db = roomBuilder.build()
        return if(db is SyncableDoorDatabase) {
            db.wrap(dbClass as KClass<SyncableDoorDatabase>) as T
        }else {
            db
        }
    }

    actual fun addCallback(callback: DoorDatabaseCallback) : DatabaseBuilder<T> {
        roomBuilder.addCallback(object: RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase)  = callback.onCreate(db)

            override fun onOpen(db: SupportSQLiteDatabase) = callback.onOpen(db)
        })

        return this
    }

    actual fun addMigrations(vararg migrations: DoorMigration): DatabaseBuilder<T> {
        roomBuilder.addMigrations(*migrations)
        return this
    }


}