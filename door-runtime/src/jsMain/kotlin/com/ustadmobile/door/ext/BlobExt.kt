package com.ustadmobile.door.ext

import com.ustadmobile.door.attachments.SparkMD5
import kotlinx.coroutines.await
import org.w3c.files.Blob
import kotlin.js.Promise

suspend fun Blob.md5(): String {
    val arrayBufferPromise: Promise<*> = asDynamic().arrayBuffer()
    val arrayBuffer = arrayBufferPromise.await()

    return SparkMD5.ArrayBuffer.hash(arrayBuffer)
}

