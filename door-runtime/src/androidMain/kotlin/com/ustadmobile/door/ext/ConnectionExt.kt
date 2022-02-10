package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.Connection

actual inline fun <R> Connection.useConnection(block: (Connection) -> R) : R {
    try {
        return block(this)
    }catch(e: Exception) {
        throw e
    }finally {
        close()
    }
}
