package com.ustadmobile.lib.annotationprocessor.core

import com.squareup.kotlinpoet.TypeName

/**
 * Determine if the given SQL runs a query that could modify a table. This will return true
 * for insert, update, delete, and replace. It will return false for other (e.g. select) queries
 */
fun String.isSQLAModifyingQuery() : Boolean {
    val queryTrim = lowercase().trim()
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


fun String.withSuffixIf(suffix: String, condition: (String) -> Boolean) : String{
    if(condition(this))
        return "$this$suffix"
    else
        return this
}

/**
 * Shorthand to replace all named parameters in a query (e.g. :param) with ? placeholders
 */
fun String.replaceQueryNamedParamsWithQuestionMarks(
    queryNamedParams: List<String> = getSqlQueryNamedParameters()
): String {
    var newSql = this
    queryNamedParams.forEach { newSql = newSql.replace(":$it", "?") }
    return newSql
}

/**
 * Shorthand to replace all named parameters
 */
fun String.replaceQueryNamedParamsWithDefaultValues(
    queryNamedParams: Map<String, TypeName>
) : String{
    var newSql = this
    queryNamedParams.forEach {
        newSql = newSql.replace(":${it.key}", defaultSqlQueryVal(it.value))
    }

    return newSql
}

/**
 * For SQL with named parameters (e.g. "SELECT * FROM Table WHERE uid = :paramName") return a
 * list of all named parameters.
 *
 * @receiver String representing SQL that may have named parameters e.g. :paramName
 * @returns list of named parameters contained within the query
 */
fun String.getSqlQueryNamedParameters(): List<String> {
    val namedParams = mutableListOf<String>()
    var insideQuote = false
    var insideDoubleQuote = false
    var lastC: Char = 0.toChar()
    var startNamedParam = -1
    for (i in 0 until this.length) {
        val c = this[i]
        if (c == '\'' && lastC != '\\')
            insideQuote = !insideQuote
        if (c == '\"' && lastC != '\\')
            insideDoubleQuote = !insideDoubleQuote

        if (!insideQuote && !insideDoubleQuote) {
            if (c == ':') {
                startNamedParam = i
            } else if (!(Character.isLetterOrDigit(c) || c == '_') && startNamedParam != -1) {
                //process the parameter
                namedParams.add(this.substring(startNamedParam + 1, i))
                startNamedParam = -1
            } else if (i == this.length - 1 && startNamedParam != -1) {
                namedParams.add(this.substring(startNamedParam + 1, i + 1))
                startNamedParam = -1
            }
        }


        lastC = c
    }

    return namedParams
}

/**
 * Where SQLite and Postgres disagree, sometimes it is possible to simply add additional (postgres only) comments and
 * change SQLite's REPLACE INTO into INSERT INTO ... ON CONFLICT for Postgres.
 *
 */
fun String.sqlToPostgresSql() : String {
    val uppercaseSql = uppercase()
    var newSql = this
    if(uppercaseSql.trimStart().startsWith("REPLACE INTO"))
        newSql = "INSERT INTO" + this.substring(newSql.indexOf("REPLACE INTO") + "REPLACE INTO".length)

    var pgSectionIndex: Int
    while(newSql.indexOf(AbstractDbProcessor.PGSECTION_COMMENT_PREFIX).also { pgSectionIndex = it } != -1) {
        val pgSectionEnd = newSql.indexOf("*/", pgSectionIndex)
        newSql = newSql.substring(0, pgSectionIndex) +
                newSql.substring(pgSectionIndex + AbstractDbProcessor.PGSECTION_COMMENT_PREFIX.length, pgSectionEnd) +
                newSql.substring(pgSectionEnd + 2)
    }

    return newSql
}
