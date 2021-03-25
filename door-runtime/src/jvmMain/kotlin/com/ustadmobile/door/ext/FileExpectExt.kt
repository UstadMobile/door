package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorUri
import java.io.File

actual fun File.toDoorUri(): DoorUri = DoorUri(this.toURI())
