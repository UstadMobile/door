package com.ustadmobile.door

/**
 * DoorUri is a simple wrapper for the underlying system Uri class. This is android.net.Uri on
 * Android and java.net.URI on JVM.
 */
actual class DoorUri {
    actual companion object {
        actual fun parse(uriString: String): DoorUri {
            TODO("Not yet implemented")
        }
    }

}