package com.ustadmobile.door.ext

import io.ktor.http.ContentType

/**
 * Shorthand extension function for contentType to be sent using the UTF8 charset.
 */
fun ContentType.withUtf8Charset(): ContentType = withParameter("charset", "utf-8")