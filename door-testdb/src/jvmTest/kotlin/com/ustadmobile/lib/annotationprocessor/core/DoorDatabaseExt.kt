package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.DoorDatabaseRepository

/**
 * Convenience method for testing so that a repository counts itself as connected immediately
 */
inline fun <reified  T : RoomDatabase> T.asConnectedRepository(): T {
    (this as DoorDatabaseRepository).connectivityStatus = DoorDatabaseRepository.STATUS_CONNECTED
    return this
}