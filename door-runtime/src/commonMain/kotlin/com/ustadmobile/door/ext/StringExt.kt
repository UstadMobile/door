package com.ustadmobile.door.ext

fun String.hexStringToByteArray() = this.chunked(2).map { it.toInt(16).toByte() }.toByteArray()


private val STR_REGEX : Regex by lazy(LazyThreadSafetyMode.NONE) {
    Regex("\\s\\s+")
}

/**
 * Attempt to remove excess white space in SQL. This currently only uses a regex to replace any block of white space
 * with a single space.
 */
fun String.minifySql(): String {
    return STR_REGEX.replace(this, " ")
}

private val sanitizeRegex: Regex by lazy(LazyThreadSafetyMode.NONE) {
    Regex("\\W")
}

fun String.sanitizeDbName(): String {
    return  this.removePrefix("https://")
        .removePrefix("http://")
        .replace(sanitizeRegex, "_")
}