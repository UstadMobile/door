package com.ustadmobile.door.util

import android.content.Context
import androidx.room.RoomDatabase
import java.util.*
import com.ustadmobile.door.ext.rootDatabase
import java.io.Closeable

/**
 * A DoorAndroidRoomHelper is created for every RoomDatabase built and retrieved using a
 * WeakHashMap. It provides a way to store additional properties that are not supported on room
 * e.g. the attachmentsDir, ReplicationNotificationDispatcher, etc.
 */
class DoorAndroidRoomHelper(
    val db: RoomDatabase,
    val context: Context,
) : Closeable {

    val nodeIdAndAuthCache: NodeIdAuthCache by lazy {
        NodeIdAuthCache(db)
    }

    override fun close() {

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
        ) {
            doorAndroidRoomHelperMap.getOrPut(db) {
                DoorAndroidRoomHelper(db, context)
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