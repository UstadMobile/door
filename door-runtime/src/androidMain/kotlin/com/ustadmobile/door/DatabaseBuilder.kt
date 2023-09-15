package com.ustadmobile.door

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.ext.execSqlBatch
import com.ustadmobile.door.ext.isWrappable
import com.ustadmobile.door.message.DefaultDoorMessageCallback
import com.ustadmobile.door.message.DoorMessageCallback
import com.ustadmobile.door.migration.DoorMigration
import com.ustadmobile.door.triggers.DoorTriggerCallback
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST", "unused") //This is used as an API
class DatabaseBuilder<T: RoomDatabase>(
    private val roomBuilder: RoomDatabase.Builder<T>,
    private val dbClass: KClass<T>,
    private val nodeId: Long,
    private var messageCallback: DoorMessageCallback<T> = DefaultDoorMessageCallback(),
) {

    companion object {
        fun <T : RoomDatabase> databaseBuilder(
            context: Context,
            dbClass: KClass<T>,
            dbName: String,
            nodeId: Long,
        ): DatabaseBuilder<T> {
            val roomDatabaseBuilder = Room.databaseBuilder(context.applicationContext, dbClass.java, dbName)
            roomDatabaseBuilder.addCallback(DoorTriggerCallback(dbClass.doorDatabaseMetadata().version, dbClass))
            return DatabaseBuilder(roomDatabaseBuilder, dbClass, nodeId)
        }
    }


    fun build(): T {
        val roomDb = roomBuilder.build()
        return if(roomDb.isWrappable(dbClass)) {
            val wrapperClass = Class.forName("${dbClass.java.canonicalName}${DoorDatabaseWrapper.SUFFIX}") as Class<T>
            return wrapperClass.getConstructor(dbClass.java, Long::class.javaPrimitiveType, DoorMessageCallback::class.java)
                .newInstance(roomDb, nodeId, messageCallback)
        }else {
            roomDb
        }
    }

    fun messageCallback(messageCallback: DoorMessageCallback<T>): DatabaseBuilder<T> {
        this.messageCallback = messageCallback
        return this
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