package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorUri
import java.io.File
import java.nio.file.Paths

actual fun DoorUri.toFile(): File {
    return Paths.get(this.uri).toFile()
}
