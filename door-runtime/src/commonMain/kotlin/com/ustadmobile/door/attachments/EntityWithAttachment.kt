package com.ustadmobile.door.attachments

/**
 * This interface can be implemented by a generated wrapper class.
 */
interface EntityWithAttachment {

    var attachmentUri: String?

    var attachmentMd5: String?

    var attachmentSize: Int

    val tableName: String

}