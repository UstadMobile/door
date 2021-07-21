package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorUri
import java.io.File
import java.io.InputStream
import java.nio.file.Paths

actual fun DoorUri.toFile(): File {
    return Paths.get(this.uri).toFile()
}

actual fun DoorUri.openInputStream(context: Any): InputStream?{
    return this.uri.toURL().openStream()
}
