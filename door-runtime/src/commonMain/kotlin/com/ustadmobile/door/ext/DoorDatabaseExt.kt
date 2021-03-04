package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase

/**
 * Get the database type that is running on the given database (DoorDbType.SQLITE Or DoorDbType.POSTGRES)
 */
expect fun DoorDatabase.dbType(): Int

expect fun DoorDatabase.dbSchemaVersion(): Int

/**
 * Run a transaction within a suspend coroutine context.
 */
expect suspend inline fun <T: DoorDatabase, R> T.doorWithTransaction(crossinline block: suspend(T) -> R): R
