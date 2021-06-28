package com.ustadmobile.door.ext

/**
 * Turn the given string map into a URL query string (encoding both the key and value)
 */
fun Map<String, String>.toUrlQueryString(): String {
    return this.entries.map { "${it.key.urlEncode()}=${it.value.urlEncode()}" }.joinToString(separator = "&")
}