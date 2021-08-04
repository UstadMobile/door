package com.ustadmobile.door

import androidx.sqlite.db.SimpleSQLiteQuery

fun expandArrayParams(sql: String, values: Array<out Any?>?): DoorQuery {
    //do the expansion

    var newSql = sql
    val newParams = mutableListOf<Any?>()
    var fromIndex = 0

    values?.forEach {

        if (it !is List<*>) {
            newParams.add(it)
            fromIndex = newSql.indexOf("?", fromIndex) + 1
        } else {

            val args = mutableListOf<String>()
            repeat(it.count()) { args.add("?") }
            val replacement = args.joinToString(",")
            fromIndex = newSql.indexOf("?", fromIndex)
            newSql = newSql.replaceRange(fromIndex, fromIndex + 1, replacement)
            it.forEach { newParams.add(it) }
            fromIndex += replacement.length
        }


    }

    return SimpleSQLiteQuery(newSql, newParams.toTypedArray())
}

class SimpleDoorQueryImpl(val actualQuery: DoorQuery) : DoorQuery by actualQuery {

    constructor(sql: String, values: Array<out Any?>?) : this(expandArrayParams(sql, values))
}


actual typealias SimpleDoorQuery = SimpleDoorQueryImpl