package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.room.RoomDatabase

/**
 * Interface that represents a local attachment storage backend. This could be a filesystem based backend (e.g. on Android/JVM),
 * Indexeddb (JS), or HttpSqlJs (JS).
 */
interface AttachmentStorage {

    /**
     * Store an attachment for the given entity. This must
     *  1. Retrieve the data from EntityWithAttachment.attachmentUri if it is not already start with door-attachment://
     *  2. Save the attachment data locally (e.g. to file or indexeddb etc)
     *  3. Update the attachmentUri to door-attachment://tablename/md5
     *  4. Set the md5Sum and AttachmentSize fields
     */
    suspend fun storeAttachment(entityWithAttachment: EntityWithAttachment)

    /**
     * Retrieve a URI that can be used to access the attachment data. This Uri must start with door-attachment://
     */
    suspend fun retrieveAttachment(attachmentUri: String): DoorUri

}
