package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDbType

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

const val POSTGRES_SELECT_IN_REPLACEMENT = "IN (SELECT UNNEST(?))"

val POSTGRES_SELECT_IN_PATTERN = Regex("IN(\\s*)\\((\\s*)\\?(\\s*)\\)", RegexOption.IGNORE_CASE)

fun String.adjustQueryWithSelectInParam(jdbcDbType: Int): String {
    return if(jdbcDbType == DoorDbType.POSTGRES) {
        POSTGRES_SELECT_IN_PATTERN.replace(this, POSTGRES_SELECT_IN_REPLACEMENT)
    }else {
        this
    }
}
