package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.Connection

actual fun <R> Connection.useConnection(block: (Connection) -> R) : R {
    try {
        return block(this)
    }catch(e: Exception) {
        if(!autoCommit)
            rollback()

        throw e
    }finally {
        close()
    }
}

actual suspend fun <R> Connection.useConnectionAsync(
    block: suspend (Connection) -> R
) : R {
    try {
        return block(this)
    }catch(e: Exception) {
        if(!autoCommit)
            rollback()

        throw e
    }finally {
        close()
    }
}