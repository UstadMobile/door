package com.ustadmobile.door

import android.content.Context
import androidx.room.Room
import kotlin.reflect.KClass
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ustadmobile.door.ext.isWrappable
import com.ustadmobile.door.ext.wrap
import com.ustadmobile.door.migration.DoorMigration

@Suppress("UNCHECKED_CAST", "unused") //This is used as an API
actual class DatabaseBuilder<T: DoorDatabase>(
    private val roomBuilder: RoomDatabase.Builder<T>,
    private val dbClass: KClass<T>
) {

    companion object {
        fun <T : DoorDatabase> databaseBuilder(context: Any, dbClass: KClass<T>, dbName: String): DatabaseBuilder<T> {
            val applicationContext = (context as Context).applicationContext
            val builder = DatabaseBuilder(Room.databaseBuilder(applicationContext, dbClass.java, dbName),
                dbClass)

            val callbackClassName = "${dbClass.java.canonicalName}_AndroidReplicationCallback"
            println("Attempt to load callback $callbackClassName")

            val callbackClass = Class.forName(callbackClassName).newInstance() as DoorDatabaseCallback

            builder.addCallback(callbackClass)

            return builder
        }
    }



    fun build(): T {
        val db = roomBuilder.build()
        return if(db.isWrappable(dbClass)) {
            db.wrap(dbClass)
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
        roomBuilder.addMigrations(*migrations.map { it.asRoomMigration() }.toTypedArray())
        return this
    }


}