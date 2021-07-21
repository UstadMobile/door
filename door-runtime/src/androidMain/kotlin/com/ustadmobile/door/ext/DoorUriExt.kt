package com.ustadmobile.door.ext

import android.content.Context
import com.ustadmobile.door.DoorUri
import java.io.File
import androidx.core.net.toFile
import java.io.InputStream

actual fun DoorUri.toFile(): File {
    return this.uri.toFile()
}

actual fun DoorUri.openInputStream(context: Any) : InputStream? {
    return (context as Context).contentResolver.openInputStream(this.uri)
}