package com.ustadmobile.door

import com.ustadmobile.door.attachments.EntityWithAttachment

class DummyEntityWithAttachment(): EntityWithAttachment {

    override var attachmentUri: String? = null

    override var attachmentMd5: String? = null

    override var attachmentSize: Int = 0

    override var tableName: String = "DummyEntityWithAttachment"
}
