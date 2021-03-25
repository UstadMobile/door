package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorUri
import java.io.File

/**
 * Returns the given File object as a DoorUri for the underlying platform (e.g. Android or JVM)
 */
expect fun File.toDoorUri(): DoorUri
