package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorUri
import java.io.File
import java.io.InputStream

/**
 * Returns this Uri as a file, if it is a valid file. Otherwise throws IllegalStateException
 */
expect fun DoorUri.toFile(): File

/**
 * Open an InputStream for the given Uri.
 */
expect fun DoorUri.openInputStream(context: Any): InputStream?

