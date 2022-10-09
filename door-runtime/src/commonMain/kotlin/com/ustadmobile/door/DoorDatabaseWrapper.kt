package com.ustadmobile.door

import com.ustadmobile.door.attachments.AttachmentStorage
import com.ustadmobile.door.room.RoomDatabase

/**
 * The DoorDatabaseWrapper is a generated wrapper that will:
 *  - Set the primary key and update the versionId field (if modified with @LastModified) for any @ReplicateEntity
 *  - Take care of storing and deleting attachments (transferring attachments to/from a remote server is the job of
 *    the repository)
 */
interface DoorDatabaseWrapper {

    /**
     * The underlying Database. On JVM/JS this will be the Door-generated Jdbc implementation. On Android this will be
     * the normal room-generated implementation
     */
    val realDatabase: RoomDatabase

    val dbName: String

    val attachmentStorage: AttachmentStorage?

    companion object {

        /**
         * This is the class name suffix that will be used by generated ReadOnlyWrappers
         */
        const val SUFFIX = "_DoorWrapper"

    }
}