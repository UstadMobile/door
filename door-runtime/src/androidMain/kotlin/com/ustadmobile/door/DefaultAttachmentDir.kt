package com.ustadmobile.door

import android.content.Context
import androidx.core.content.ContextCompat
import java.io.File

actual fun defaultAttachmentDir(context: Any, endpointUrl: String): String {
    return File(ContextCompat.getExternalFilesDirs(context as Context, null)[0],
            "attachments").absolutePath
}