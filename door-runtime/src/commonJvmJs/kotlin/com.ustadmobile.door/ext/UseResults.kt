package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.Statement

fun <R> ResultSet.useResults(block: (ResultSet) -> R) : R{
    try {
        return block(this)
    }finally {
        close()
    }
}

inline fun <S: Statement, R> S.useStatement(block: (S) -> R) : R {
    try {
        return block(this)
    }finally {
        close()
    }
}
