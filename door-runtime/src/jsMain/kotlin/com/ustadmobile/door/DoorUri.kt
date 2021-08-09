package com.ustadmobile.door

import org.w3c.dom.url.URL

/**
 * DoorUri is a simple wrapper for the underlying system Uri class. This is android.net.Uri on
 * Android and java.net.URI on JVM.
 */
actual class DoorUri(val uri: URL)  {

    actual suspend fun getFileName(context: Any): String {
        return uri.pathname.substringAfterLast( "/")
    }

    actual companion object {
        actual fun parse(uriString: String) = DoorUri(URL(uriString))
    }
}