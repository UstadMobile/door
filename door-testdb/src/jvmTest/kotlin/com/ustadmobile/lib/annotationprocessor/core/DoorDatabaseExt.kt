package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository

/**
 * Convenience method for testing so that a repository counts itself as connected immediately
 */
inline fun <reified  T : DoorDatabase> T.asConnectedRepository(): T {
    (this as DoorDatabaseRepository).connectivityStatus = DoorDatabaseRepository.STATUS_CONNECTED
    return this
}