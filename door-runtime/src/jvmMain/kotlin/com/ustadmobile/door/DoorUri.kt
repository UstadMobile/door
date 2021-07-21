package com.ustadmobile.door

import java.net.URI

actual class DoorUri(val uri: URI) {

    actual suspend fun getFileName(context: Any): String {
        return uri.path.substringAfterLast("/")
    }

    actual companion object {
        actual fun parse(uriString: String) = DoorUri(URI(uriString))
    }


}