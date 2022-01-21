package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorUri

fun DoorDatabase.requireAttachmentStorageUri() : DoorUri {
    return attachmentsStorageUri ?: throw IllegalStateException("Database constructed without attachment storage dir! " +
            "Please set this on the builder!")
}
