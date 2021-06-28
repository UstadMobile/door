package com.ustadmobile.door.ext

/**
 * Encode the given string using URL encoding
 */
//url is used by js code
@Suppress("UNUSED_VARIABLE")
actual fun String.urlEncode(): String {
    val url = this
    return js("encodeURI(url)") as String
}