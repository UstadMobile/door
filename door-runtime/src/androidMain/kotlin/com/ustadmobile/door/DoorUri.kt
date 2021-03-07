package com.ustadmobile.door

import android.net.Uri

actual class DoorUri(val uri: Uri) {

    actual companion object {
        actual fun parse(uriString: String) : DoorUri = DoorUri(Uri.parse(uriString))
    }

}
