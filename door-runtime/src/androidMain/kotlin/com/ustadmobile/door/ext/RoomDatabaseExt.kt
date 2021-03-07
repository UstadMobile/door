package com.ustadmobile.door.ext

import androidx.room.RoomDatabase
import com.github.aakira.napier.Napier
import com.ustadmobile.door.DoorSyncCallback

/**
 * This extension function is used by the generated clearAllTables function on Android. It must
 * be run after calling clearAllTables.
 *
 * This is done automatically by the onOpen callback that is automatically generated and then added
 * by DoorDatabaseBuilder on Android.
 */
fun RoomDatabase.initSyncablePrimaryKeys() {
    try {
        val syncCallback= Class.forName("${this::class.java.superclass?.canonicalName}_SyncCallback") as DoorSyncCallback
        openHelper.writableDatabase.use {
            syncCallback.initSyncablePrimaryKeys(it)
        }
    }catch(e: Exception) {
        Napier.e("Exception initializing syncable entities", e)
    }
}