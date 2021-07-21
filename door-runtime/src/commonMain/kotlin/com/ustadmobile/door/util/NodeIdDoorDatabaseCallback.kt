package com.ustadmobile.door.util

import com.ustadmobile.door.DoorDatabaseCallback
import com.ustadmobile.door.DoorSqlDatabase

/**
 * Used to set and find the node id. NodeId is tricky because it is stored in the database, but needed a lot by the
 * repository.
 */
class NodeIdDoorDatabaseCallback: DoorDatabaseCallback {

    override fun onCreate(db: DoorSqlDatabase) {
        //
    }

    override fun onOpen(db: DoorSqlDatabase) {

    }
}