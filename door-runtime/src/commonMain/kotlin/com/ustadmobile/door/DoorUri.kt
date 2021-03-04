package com.ustadmobile.door

/**
 * DoorUri is a simple wrapper for the underlying system Uri class. This is android.net.Uri on
 * Android and java.net.URI on JVM.
 */
expect class DoorUri {

    companion object {
        fun parse(uriString: String): DoorUri
    }
}