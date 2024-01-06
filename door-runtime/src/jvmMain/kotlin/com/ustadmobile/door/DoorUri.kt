package com.ustadmobile.door

import java.net.URI

actual class DoorUri(val uri: URI) {

    actual suspend fun getFileName(context: Any): String {
        return if(uri.path.endsWith("/")) {
            uri.path.removeSuffix("/").substringAfterLast("/")
                .substringBeforeLast("?")
        }else {
            uri.path.substringAfterLast("/").substringBeforeLast("?")
        }
    }

    override fun equals(other: Any?): Boolean {
        return (other as? DoorUri)?.uri == uri
    }

    override fun hashCode(): Int {
        return uri.hashCode()
    }

    override fun toString() = uri.toString()

    actual companion object {
        actual fun parse(uriString: String) = DoorUri(URI(uriString))
    }


}