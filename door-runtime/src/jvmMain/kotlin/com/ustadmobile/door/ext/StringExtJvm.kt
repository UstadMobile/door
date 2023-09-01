package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorConstants.NOTPGSECTION_COMMENT_PREFIX
import com.ustadmobile.door.DoorConstants.NOTPGSECTION_END_COMMENT_PREFIX
import com.ustadmobile.door.DoorConstants.PGSECTION_COMMENT_PREFIX


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
    while(newSql.indexOf(PGSECTION_COMMENT_PREFIX).also { pgSectionIndex = it } != -1) {
        val pgSectionEnd = newSql.indexOf("*/", pgSectionIndex)
        newSql = newSql.substring(0, pgSectionIndex) +
                newSql.substring(pgSectionIndex + PGSECTION_COMMENT_PREFIX.length, pgSectionEnd) +
                newSql.substring(pgSectionEnd + 2)
    }

    var inNoPgSection = false

    var newSqlLines = ""
    newSql.lines().forEach { line ->
        val trimmedLine = line.trimStart()
        if(trimmedLine.startsWith(NOTPGSECTION_COMMENT_PREFIX)) {
            inNoPgSection = true
        }

        if(!inNoPgSection)
            newSqlLines += line + "\n"

        if(trimmedLine.startsWith(NOTPGSECTION_END_COMMENT_PREFIX))
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

