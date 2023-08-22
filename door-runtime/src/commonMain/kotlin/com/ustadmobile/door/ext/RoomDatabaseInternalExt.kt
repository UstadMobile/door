package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorDatabaseWrapper
import com.ustadmobile.door.room.RoomDatabase

internal val <T: RoomDatabase> T.doorWrapper: DoorDatabaseWrapper
    get() {
        return if (this is DoorDatabaseWrapper) {
            this
        }else if(this is DoorDatabaseRepository) {
            this.db as DoorDatabaseWrapper
        }else {
            throw IllegalArgumentException("Cannot get doorWrapper for $this : it is not a wrapper or repository")
        }
    }

internal val <T: RoomDatabase> T.doorWrapperNodeId: Long
    get() = doorWrapper.nodeId


