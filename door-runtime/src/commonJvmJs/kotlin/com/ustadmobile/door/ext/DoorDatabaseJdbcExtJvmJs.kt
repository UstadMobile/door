package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseCommon
import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.util.TransactionMode

fun <R> DoorDatabaseJdbc.useConnection(
    block: (Connection) -> R
): R = useConnection(TransactionMode.READ_WRITE, block)

suspend fun <R> DoorDatabaseJdbc.useConnectionAsync(
    block: suspend (Connection) -> R
): R = useConnectionAsync(TransactionMode.READ_WRITE, block)

/**
 * On JVM and JS, DoorDatabase is ALWAYS a child class of DoorDatabaseCommon. Unfortunately, because this is not part
 * of the expect/actual, the compiler doesn't realize this.
 */
//In reality: It will always succeed: DoorDatabaseCommon is the parent class. Kotlin Multiplatform is confused here.
@Suppress("CAST_NEVER_SUCCEEDS")
fun DoorDatabase.asCommon(): DoorDatabaseCommon = (this as DoorDatabaseCommon)
