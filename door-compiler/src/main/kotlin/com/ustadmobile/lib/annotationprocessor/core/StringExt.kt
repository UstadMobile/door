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

    var inNoPgSection = false

    var newSqlLines = ""
    newSql.lines().forEach { line ->
        val trimmedLine = line.trimStart()
        if(trimmedLine.startsWith(AbstractDbProcessor.NOTPGSECTION_COMMENT_PREFIX)) {
            inNoPgSection = true
        }

        if(!inNoPgSection)
            newSqlLines += line + "\n"

        if(trimmedLine.startsWith(AbstractDbProcessor.NOTPGSECTION_END_COMMENT_PREFIX))
            inNoPgSection = false
    }


    return newSqlLines
}

fun String.useAsPostgresSqlIfNotBlankOrFallback(generalSql: String): String {
    return if(this != "")
        this
    else
        generalSql.sqlToPostgresSql()
}

fun Array<String>.useAsPostgresSqlIfNotEmptyOrFallback(generalSql: Array<String>) : Array<String> {
    return if(isNotEmpty())
        this
    else
        generalSql.map { it.sqlToPostgresSql() }.toTypedArray()
}

/**
 * Used where we need to test a query, but a field name does not exist because it's not inside a trigger
 * (e.g. NEW.someField). This uses a regex to avoid issues where one field name is a substring of another.
 */
fun String.replaceColNameWithDefaultValueInSql(fieldName: String, substitution: String) : String {
    return "([\\s,)(])$fieldName([\\s,)(])".toRegex().replace(this) {
        it.groupValues[1] + substitution + it.groupValues[2]
    }
}
