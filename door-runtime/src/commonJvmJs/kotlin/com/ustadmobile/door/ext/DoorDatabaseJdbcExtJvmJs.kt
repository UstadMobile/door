package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.util.TransactionMode

inline fun <R> DoorDatabaseJdbc.useConnection(
    block: (Connection) -> R
): R = useConnection(TransactionMode.READ_WRITE, block)
