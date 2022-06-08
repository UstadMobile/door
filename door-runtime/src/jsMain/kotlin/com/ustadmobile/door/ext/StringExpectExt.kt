package com.ustadmobile.door.ext

import com.ustadmobile.door.util.encodeURIComponent

/**
 * Encode the given string using URL encoding
 */
//url is used by js code
@Suppress("UNUSED_VARIABLE")
actual fun String.urlEncode() = encodeURIComponent(this)
