package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorSqlDatabase

/**
 * Get the database type that is running on the given database (DoorDbType.SQLITE Or DoorDbType.POSTGRES)
 */
actual fun DoorDatabase.dbType(): Int {
    TODO("Not yet implemented")
}

actual fun DoorDatabase.dbSchemaVersion(): Int {
    TODO("Not yet implemented")
}

/**
 * Run a transaction within a suspend coroutine context.
 */
actual suspend inline fun <T : DoorDatabase, R> T.doorWithTransaction(crossinline block: suspend (T) -> R): R {
    TODO("Not yet implemented")
}

actual fun DoorSqlDatabase.dbType(): Int {
    TODO("Not yet implemented")
}