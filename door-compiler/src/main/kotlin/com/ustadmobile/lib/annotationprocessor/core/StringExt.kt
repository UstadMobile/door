package com.ustadmobile.lib.annotationprocessor.core

import java.util.*

/**
 * Determine if the given SQL runs a query that could modify a table. This will return true
 * for insert, update, delete, and replace. It will return false for other (e.g. select) queries
 */
fun String.isSQLAModifyingQuery() : Boolean {
    val queryTrim = toLowerCase(Locale.ROOT).trim()
    return listOf("update", "delete", "insert", "replace").any { queryTrim.startsWith(it) }
}

/**
 * Remove any instance of the given prefix from the string repeatedly
 * until the string no longer begins with the given prefix.
 */
fun String.removeAllPrefixedInstancesOf(prefix: String) : String {
    var str = this

    do {
        str = str.removePrefix(prefix)
    }while (str.startsWith(prefix))

    return str
}

/**
 * Remove any instance of the given suffix from the string repeatedly
 * until the string no longer ends with the given suffix.
 */
fun String.removeAllSuffixedInstancesOf(suffix: String): String {
    var str = this

    do {
        str = str.removeSuffix(suffix)
    }while (str.endsWith(suffix))

    return str
}

