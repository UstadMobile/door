package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.Connection

inline fun <R> Connection.useConnection(
    block: (Connection) -> R
) : R {
    try {
        return block(this)
    }finally {
        close()
    }
}
