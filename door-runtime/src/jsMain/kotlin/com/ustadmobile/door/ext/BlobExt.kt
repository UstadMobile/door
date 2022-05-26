package com.ustadmobile.door.ext

import com.ustadmobile.door.attachments.SparkMD5
import org.w3c.files.Blob

@Suppress("RedundantSuspendModifier") //This should probably be moved to a webworker in future.
suspend fun Blob.md5(): String {
    return SparkMD5.ArrayBuffer.hash(this)
}

