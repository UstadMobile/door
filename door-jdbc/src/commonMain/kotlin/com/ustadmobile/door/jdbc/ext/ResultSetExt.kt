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
