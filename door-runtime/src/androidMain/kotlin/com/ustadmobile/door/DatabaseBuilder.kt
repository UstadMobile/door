package com.ustadmobile.door

import android.content.Context
import androidx.room.Room
import kotlin.reflect.KClass
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ustadmobile.door.attachments.AttachmentFilter
import com.ustadmobile.door.ext.execSqlBatch
import com.ustadmobile.door.ext.isWrappable
import com.ustadmobile.door.ext.wrap
import com.ustadmobile.door.migration.DoorMigration
import com.ustadmobile.door.util.DeleteZombieAttachmentsListener
import com.ustadmobile.door.util.DoorAndroidRoomHelper
import java.io.File

@Suppress("UNCHECKED_CAST", "unused") //This is used as an API
class DatabaseBuilder<T: RoomDatabase>(
    private val roomBuilder: RoomDatabase.Builder<T>,
    private val dbClass: KClass<T>,
    private val appContext: Context,
    private val nodeId: Long,
    private val attachmentsDir: File?,
    private val attachmentFilters: List<AttachmentFilter>,
) {

    companion object {
        fun <T : RoomDatabase> databaseBuilder(
            context: Any,
            dbClass: KClass<T>,
            dbName: String,
            nodeId: Long,
            attachmentsDir: File? = null,
            attachmentFilters: List<AttachmentFilter> = listOf(),
        ): DatabaseBuilder<T> {
            val applicationContext = (context as Context).applicationContext
            val builder = DatabaseBuilder(Room.databaseBuilder(applicationContext, dbClass.java, dbName),
                dbClass, applicationContext, nodeId, attachmentsDir, attachmentFilters)

            val callbackClassName = "${dbClass.java.canonicalName}_AndroidReplicationCallback"
            println("Attempt to load callback $callbackClassName")

            val callbackClass = Class.forName(callbackClassName).newInstance() as DoorDatabaseCallbackSync

            builder.addCallback(callbackClass)

            return builder
        }
    }



    fun build(): T {
        val db = roomBuilder.build()
        DoorAndroidRoomHelper.createAndRegisterHelper(db, appContext, attachmentsDir, attachmentFilters,
            DeleteZombieAttachmentsListener(db))
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