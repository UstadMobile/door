package com.ustadmobile.door

import androidx.room.RoomDatabase

/**
 * When running an insert or update query on an entity with @ReplicateEntity, the primary key and versionId fields
 * need to be managed. The generated implementation wrapper takes care of assigning a unique primary key using the
 * DoorPrimaryKeyManager and updates the versionId field if it was annotated with @LastModified
 */
interface DoorDatabaseReplicateWrapper {

    val realDatabase: RoomDatabase

    val dbName: String

    companion object {

        /**
         * This is the class name suffix that will be used by generated ReadOnlyWrappers
         */
        const val SUFFIX = "_ReplicateWrapper"

    }
}