package com.ustadmobile.door.ext

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
