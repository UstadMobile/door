package com.ustadmobile.door

import android.content.Context
import android.net.Uri
import com.ustadmobile.door.ext.getFileName

actual class DoorUri(val uri: Uri) {

    actual suspend fun getFileName(context: Any): String {
        return (context as Context).contentResolver.getFileName(uri)
    }

    override fun toString() = uri.toString()

    actual companion object {
        actual fun parse(uriString: String) : DoorUri = DoorUri(Uri.parse(uriString))
    }


}
