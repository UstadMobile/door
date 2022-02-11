package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.util.TransactionMode

/**
 * This is the main way that a Connection on the database should be used. It will setup any change tracking that is
 * required, and deliver change events to the invalidationTracker
 */
expect inline fun <R> DoorDatabaseJdbc.useConnection(
    transactionMode: TransactionMode,
    block: (Connection) -> R,
): R
