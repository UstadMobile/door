package com.ustadmobile.door

import java.net.URI

actual class DoorUri(val uri: URI) {

    actual companion object {
        actual fun parse(uriString: String) = DoorUri(URI(uriString))
    }

}