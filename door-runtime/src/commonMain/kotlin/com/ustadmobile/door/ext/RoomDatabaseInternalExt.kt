package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorDatabaseWrapper
import com.ustadmobile.door.room.RoomDatabase

@Suppress("UNCHECKED_CAST")
internal val <T: RoomDatabase> T.doorWrapper: DoorDatabaseWrapper<T>
    get() {
        return when (this) {
            is DoorDatabaseWrapper<*> -> {
                this as DoorDatabaseWrapper<T>
            }

            is DoorDatabaseRepository -> {
                this.db as DoorDatabaseWrapper<T>
            }

            else -> {
                throw IllegalArgumentException("Cannot get doorWrapper for $this : it is not a wrapper or repository")
            }
        }
    }

val <T: RoomDatabase> T.doorWrapperNodeId: Long
    get() = doorWrapper.nodeId


