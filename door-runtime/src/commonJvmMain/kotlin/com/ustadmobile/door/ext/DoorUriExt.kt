package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorUri
import java.io.File

/**
 * Returns this Uri as a file, if it is a valid file. Otherwise throws IllegalStateException
 */
expect fun DoorUri.toFile(): File

