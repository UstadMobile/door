package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.SyncableDoorDatabase

/**
 * Convenience method for testing so that a repository counts itself as connected immediately
 */
inline fun <reified  T : SyncableDoorDatabase> T.asConnectedRepository(): T {
    (this as DoorDatabaseRepository).connectivityStatus = DoorDatabaseRepository.STATUS_CONNECTED
    return this
}