package com.ustadmobile.door

import android.content.Context
import androidx.room.Room
import kotlin.reflect.KClass
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ustadmobile.door.ext.execSqlBatch
import com.ustadmobile.door.ext.isWrappable
import com.ustadmobile.door.ext.wrap
import com.ustadmobile.door.migration.DoorMigration

@Suppress("UNCHECKED_CAST", "unused") //This is used as an API
class DatabaseBuilder<T: RoomDatabase>(
    private val roomBuilder: RoomDatabase.Builder<T>,
    private val dbClass: KClass<T>,
    private val appContext: Context,
    private val nodeId: Long,
) {

    companion object {
        fun <T : RoomDatabase> databaseBuilder(
            context: Any,
            dbClass: KClass<T>,
            dbName: String,
            nodeId: Long,
        ): DatabaseBuilder<T> {
            val applicationContext = (context as Context).applicationContext
            val builder = DatabaseBuilder(Room.databaseBuilder(applicationContext, dbClass.java, dbName),
                dbClass, applicationContext, nodeId)

            val callbackClassName = "${dbClass.java.canonicalName}_AndroidReplicationCallback"
            println("Attempt to load callback $callbackClassName")

            val callbackClass = Class.forName(callbackClassName).newInstance() as DoorDatabaseCallbackSync

            builder.addCallback(callbackClass)

            return builder
        }
    }



    fun build(): T {
        val db = roomBuilder.build()
        return if(db.isWrappable(dbClass)) {
            db.wrap(dbClass, nodeId)
        }else {
            db
        }
    }

    fun addCallback(callback: DoorDatabaseCallback) : DatabaseBuilder<T> {
        roomBuilder.addCallback(object: RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                when(callback) {
                    is DoorDatabaseCallbackSync -> callback.onCreate(db)
                    is DoorDatabaseCallbackStatementList ->
                        db.execSqlBatch(callback.onCreate(db).toTypedArray())
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                when(callback){
                    is DoorDatabaseCallbackSync -> callback.onOpen(db)
                    is DoorDatabaseCallbackStatementList ->
                        db.execSqlBatch(callback.onOpen(db).toTypedArray())
                }
            }
        })

        return this
    }

    fun addMigrations(vararg migrations: DoorMigration): DatabaseBuilder<T> {
        roomBuilder.addMigrations(*migrations.map { it.asRoomMigration() }.toTypedArray())
        return this
    }


}