package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.ext.closeAsyncOrFallback
import com.ustadmobile.door.jdbc.Statement

inline fun <S:Statement, R> S.useStatement(block: (S) -> R) : R{
    try {
        return block(this)
    }catch(e: Exception) {
        throw e
    }finally {
        close()
    }
}

suspend inline fun <S:Statement, R> S.useStatementAsync(block: suspend (S) -> R) : R{
    try {
        return block(this)
    }catch(e: Exception) {
        throw e
    }finally {
        closeAsyncOrFallback()
    }
}
