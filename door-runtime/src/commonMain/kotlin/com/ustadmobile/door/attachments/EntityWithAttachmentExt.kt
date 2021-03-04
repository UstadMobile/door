package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorDatabaseRepository

/**
 * Shorthand for tableName/attachmentMd5. The attachment should be stored in (repo attachment dir)/relativePath.
 */
internal val EntityWithAttachment.tableNameAndMd5Path: String
    get() = "$tableName/$attachmentMd5"

/**
 *
 */
internal fun EntityWithAttachment.makeAttachmentUriFromTableNameAndMd5(): String = "${DoorDatabaseRepository.DOOR_ATTACHMENT_URI_SCHEME}://$tableName/$attachmentMd5"
