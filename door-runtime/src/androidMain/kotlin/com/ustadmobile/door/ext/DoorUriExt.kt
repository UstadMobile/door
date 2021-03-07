package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorUri
import java.io.File
import androidx.core.net.toFile

actual fun DoorUri.toFile(): File {
    return this.uri.toFile()
}