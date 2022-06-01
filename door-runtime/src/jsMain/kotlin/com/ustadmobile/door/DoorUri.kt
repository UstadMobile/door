package com.ustadmobile.door

import org.w3c.dom.url.URL
import org.w3c.files.Blob

/**
 * DoorUri is a simple wrapper for the underlying system Uri class. This is android.net.Uri on
 * Android, java.net.URI on JVM, URL on Javascript.
 */
actual class DoorUri(val uri: URL)  {

    data class DoorUriProps(val uri: String, val fileName: String?, val mimeType: String?)

    actual suspend fun getFileName(context: Any): String {
        return doorUriInfo[uri.toString()]?.fileName ?: uri.pathname.substringAfterLast( "/")
    }

    override fun toString() = uri.toString()

    actual companion object {
        actual fun parse(uriString: String) = DoorUri(URL(uriString))

        private val doorUriInfo = mutableMapOf<String, DoorUriProps>()

        /**
         * Important: This uses URL.createLocalUrl. When finished, it must be released using revokeLocalUri which
         * will call revokeLocalUrl. Failing to do so causes a memory leak.
         */
        fun createLocalUri(blob: Blob, filename: String, mimeType: String? = null): DoorUri {
            val localUrl = URL.createObjectURL(blob)
            doorUriInfo[localUrl] = DoorUriProps(localUrl, filename, mimeType)
            return DoorUri(URL(localUrl))
        }

        /**
         * Semi-internal - this can be used to lookup previously recorded information about the mime type and filename
         * of a uri that was created using file.toDoorUri
         */
        fun getDoorUriProps(uri: String)  = doorUriInfo[uri]

        fun revokeLocalUri(localUri: DoorUri) {
            URL.revokeObjectURL(localUri.toString())
            doorUriInfo.remove(localUri.toString())
        }

    }
}