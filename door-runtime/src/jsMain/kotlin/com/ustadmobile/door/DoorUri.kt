package com.ustadmobile.door

import org.w3c.dom.url.URL
import org.w3c.files.Blob

/**
 * DoorUri is a simple wrapper for the underlying system Uri class. This is android.net.Uri on
 * Android, java.net.URI on JVM, URL on Javascript.
 */
actual class DoorUri(val uri: URL)  {

    actual suspend fun getFileName(context: Any): String {
        return filenameMap[uri.toString()] ?: uri.pathname.substringAfterLast( "/")
    }

    override fun toString() = uri.toString()

    actual companion object {
        actual fun parse(uriString: String) = DoorUri(URL(uriString))

        private val filenameMap = mutableMapOf<String, String>()

        /**
         * Important: This uses URL.createLocalUrl. When finished, it must be released using revokeLocalUri which
         * will call revokeLocalUrl. Failing to do so causes a memory leak.
         */
        fun createLocalUri(blob: Blob, filename: String): DoorUri {
            val localUrl = URL.createObjectURL(blob)
            filenameMap[localUrl] = filename
            return DoorUri(URL(localUrl))
        }

        fun revokeLocalUri(localUri: DoorUri) {
            URL.revokeObjectURL(localUri.toString())
            filenameMap.remove(localUri.toString())
        }

    }
}