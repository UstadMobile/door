package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.ResultSet

fun <R> ResultSet.useResults(block: (ResultSet) -> R) : R{
    try {
        return block(this)
    }finally {
        close()
    }
}

fun <R> ResultSet.mapRows(block: (ResultSet) -> R): List<R> {
    val mappedResults = mutableLinkedListOf<R>()
    while(next()) {
        mappedResults += block(this)
    }

    return mappedResults
}

@Suppress("unused") //Used by generated code
fun <R> ResultSet.mapNextRow(defaultVal: R, block: (ResultSet) -> R): R {
    return if(next())
        block(this)
    else
        defaultVal
}

@Suppress("unused") //Used by generated code
fun <R> ResultSet.mapNextRow(block: (ResultSet) -> R): R {
    return if(next())
        block(this)
    else
        throw NullPointerException("mapNextRow: no row and no default value provided")
}
