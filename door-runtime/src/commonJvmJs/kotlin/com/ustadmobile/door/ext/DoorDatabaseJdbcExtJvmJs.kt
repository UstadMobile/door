package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.util.TransactionMode

fun <R> DoorDatabaseJdbc.useConnection(
    block: (Connection) -> R
): R = useConnection(TransactionMode.READ_WRITE, block)

suspend fun <R> DoorDatabaseJdbc.useConnectionAsync(
    block: suspend (Connection) -> R
): R = useConnectionAsync(TransactionMode.READ_WRITE, block)
