package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorUri
import org.w3c.files.File

/**
 * CAUTION: This uses createLocalUri / createObjectUrl. It must be revoked when finished with to avoid memory leaks.
 */
fun File.toDoorUri() : DoorUri {
    return DoorUri.createLocalUri(this, this.name)
}
