package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.jdbc.Connection

inline fun <R> DoorDatabaseJdbc.useConnection(block: (Connection) -> R): R {
    return dataSource.getConnection().useConnection {
        block(it)
    }
}
