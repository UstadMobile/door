package com.ustadmobile.door

import java.io.File

actual fun defaultAttachmentDir(context: Any, endpointUrl: String): String {
    return File("attachments").absolutePath
}
