package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.PreparedStatementConfig
import kotlin.reflect.KClass
import com.ustadmobile.door.jdbc.*


/**
 * Get the database type that is running on the given database (DoorDbType.SQLITE Or DoorDbType.POSTGRES)
 */
expect fun DoorDatabase.dbType(): Int

expect fun DoorDatabase.dbSchemaVersion(): Int

/**
 * Execute the given block as part of a transaction. E.g.
 *
 * db.withDoorTransactionAsync(DbType::class) { transactionDb ->
 *      transactionDb.insertAsync(SomeEntity())
 *      transactionDb.updateTotalsAsync()
 * }
 *
 * Nested transactions are supported. The real commit will only happen when all nested transactions are complete.
 *
 * On Android this will use Room's own withTransaction support (which is one at a time, first come, first served).
 *
 * On JDBC this will use JDBC transaction support and create a new (wrapper) instance of the database class tied to
 * the given transaction.
 *
 */
expect suspend fun <T: DoorDatabase, R> T.withDoorTransactionAsync(dbKClass: KClass<T>, block: suspend (T) -> R) : R

/**
 * Execute the given block as part of a transaction. E.g.
 *
 * db.withDoorTransaction(DbType::class) { transactionDb ->
 *      transactionDb.insert(SomeEntity())
 *      transactionDb.updateTotals()
 * }
 *
 * On Android this will use Room's own withTransaction support (which is one at a time, first come, first served).
 *
 * On JDBC this will use JDBC transaction support and create a new (wrapper) instance of the database class tied to
 * the given transaction.
 *
 */
expect fun <T: DoorDatabase, R> T.withDoorTransaction(dbKClass: KClass<T>, block: (T) -> R) : R

/**
 * Multiplatform wrapper function that will execute raw SQL statements in a
 * batch.
 *
 * Does not return any results. Will throw an exception in the event of
 * malformed SQL.
 *
 * The name deliberately lower cases sql to avoid name clashes
 */
expect fun DoorDatabase.execSqlBatch(vararg sqlStatements: String)

expect fun <T: DoorDatabase> KClass<T>.doorDatabaseMetadata(): DoorDatabaseMetadata<T>

/**
 * Suspended wrapper that will prepare a Statement, execute a code block, and return the code block result
 */
expect suspend fun <R> DoorDatabase.prepareAndUseStatementAsync(
    stmtConfig: PreparedStatementConfig,
    block: suspend (PreparedStatement) -> R
) : R

/**
 * Wrapper that will prepare a Statement, execute a code block, and return the code block result
 */
expect fun <R> DoorDatabase.prepareAndUseStatement(
    stmtConfig: PreparedStatementConfig,
    block: (PreparedStatement) -> R
) : R

