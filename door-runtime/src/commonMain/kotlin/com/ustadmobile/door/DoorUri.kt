package com.ustadmobile.door

/**
 * DoorUri is a simple wrapper for the underlying system Uri class. This is android.net.Uri on
 * Android and java.net.URI on JVM.
 */
expect class DoorUri {

    /**
     * Get the filename of the Uri. On Android this can use the ContentResolver to find a filename that was provided
     * by a content provider (e.g. an external app). Failing that, this would normally fallback to the substring after
     * the last slash.
     *
     * @param context Context
     */
    suspend fun getFileName(context: Any): String

    companion object {
        fun parse(uriString: String): DoorUri
    }
}