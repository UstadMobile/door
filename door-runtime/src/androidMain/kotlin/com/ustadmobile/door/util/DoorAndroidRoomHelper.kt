package com.ustadmobile.door.util

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ustadmobile.door.IncomingReplicationListenerHelper
import com.ustadmobile.door.attachments.AttachmentFilter
import com.ustadmobile.door.ext.dbClassName
import com.ustadmobile.door.replication.ReplicationNotificationDispatcher
import com.ustadmobile.door.replication.ReplicationRunOnChangeRunner
import kotlinx.coroutines.GlobalScope
import java.util.*
import com.ustadmobile.door.ext.rootDatabase
import java.io.Closeable
import java.io.File

/**
 * A DoorAndroidRoomHelper is created for every RoomDatabase built and retrieved using a
 * WeakHashMap. It provides a way to store additional properties that are not supported on room
 * e.g. the attachmentsDir, ReplicationNotificationDispatcher, etc.
 */
class DoorAndroidRoomHelper(
    val db: RoomDatabase,
    val context: Context,
    val attachmentsDir: File?,
    val attachmentFilters: List<AttachmentFilter>,
    private val deleteZombieAttachmentsListener: DeleteZombieAttachmentsListener,
) : Closeable {

    val incomingReplicationListenerHelper = IncomingReplicationListenerHelper()

    val replicationNotificationDispatcher : ReplicationNotificationDispatcher by lazy {
        val dbClass = Class.forName(db.dbClassName)
        val dbClassName = db.dbClassName
        val runOnChangeRunnerClass = Class.forName("${dbClassName}_ReplicationRunOnChangeRunner")
            .getConstructor(dbClass)
            .newInstance(db) as ReplicationRunOnChangeRunner
        ReplicationNotificationDispatcher(db, runOnChangeRunnerClass, GlobalScope)
    }

    val nodeIdAndAuthCache: NodeIdAuthCache by lazy {
        NodeIdAuthCache(db)
    }

    override fun close() {
        deleteZombieAttachmentsListener.close()
    }

    companion object {

        /**
         * The Door Android room helper is mapped 1:1 with each database to provide some Door-specific functions
         */
        private val doorAndroidRoomHelperMap = WeakHashMap<RoomDatabase, DoorAndroidRoomHelper>()


        @JvmStatic
        @Synchronized
        internal fun createAndRegisterHelper(
            db: RoomDatabase,
            context: Context,
            attachmentsDir: File?,
            attachmentFilters: List<AttachmentFilter>,
            deleteZombieAttachmentsListener: DeleteZombieAttachmentsListener
        ) {
            doorAndroidRoomHelperMap.getOrPut(db) {
                DoorAndroidRoomHelper(db, context, attachmentsDir, attachmentFilters, deleteZombieAttachmentsListener)
            }
        }

        @JvmStatic
        @Synchronized
        internal fun lookupHelper(db: RoomDatabase): DoorAndroidRoomHelper {
            return doorAndroidRoomHelperMap[db.rootDatabase] ?: throw IllegalStateException("No helper registered for $db ! " +
                    "Are you sure you used the Door's DatabaseBuilder to build it?")
        }

    }

}