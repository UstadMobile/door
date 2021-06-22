package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import kotlin.reflect.KClass

/**
 * Get the database type that is running on the given database (DoorDbType.SQLITE Or DoorDbType.POSTGRES)
 */
expect fun DoorDatabase.dbType(): Int

expect fun DoorDatabase.dbSchemaVersion(): Int

/**
 * Run a transaction within a suspend coroutine context.
 */
expect suspend inline fun <T: DoorDatabase, R> T.doorWithTransaction(crossinline block: suspend(T) -> R): R

/**
 * Multiplatform wrapper function that will execute raw SQL
 * Does not return any results. Will throw an exception in the event of
 * malformed SQL.
 */
expect fun DoorDatabase.execSql(sql: String)

expect fun <T: DoorDatabase> KClass<T>.doorDatabaseMetadata(): DoorDatabaseMetadata<T>
