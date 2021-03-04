package com.ustadmobile.door.attachments

/**
 * An AttachmentFilter is used to filter attachment data e.g. shrink images etc.
 */
interface AttachmentFilter {

    suspend fun filter(entityWithAttachment: EntityWithAttachment, tmpDir: String, context: Any): EntityWithAttachment

}