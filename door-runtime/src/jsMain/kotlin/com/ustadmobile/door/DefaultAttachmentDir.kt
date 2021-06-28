package com.ustadmobile.door

/**
 * Return a default value for the attachmentsDir for a new repository. This value would be used when the user does not
 * set a value explicitly on the repoconfig builder
 */
actual fun defaultAttachmentDir(context: Any, endpointUrl: String): String {
    return "./"
}