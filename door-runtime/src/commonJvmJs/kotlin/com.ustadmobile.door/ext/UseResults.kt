package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.ResultSet

fun <R> ResultSet.useResults(block: (ResultSet) -> R) : R{
    try {
        return block(this)
    }finally {
        close()
    }
}