package com.ustadmobile.door.ext

import com.ustadmobile.door.*
import kotlin.reflect.KClass
import com.ustadmobile.door.jdbc.*
import com.ustadmobile.door.replication.ReplicationNotificationDispatcher


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
expect suspend fun <T: DoorDatabase, R> T.withDoorTransactionAsync(dbKClass: KClass<out T>, block: suspend (T) -> R) : R

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

/**
 * Sometimes we want to create a new instance of the database that is just a wrapper e.g.
 * SyncableReadOnlyWrapper, possibly a transaction wrapper. When this happens, all calls to
 * listen for changes, opening connections, etc. should be redirected to the source database
 */
expect val DoorDatabase.sourceDatabase: DoorDatabase?

expect val DoorDatabase.doorPrimaryKeyManager: DoorPrimaryKeyManager

expect fun DoorDatabase.handleTablesChanged(changeTableNames: List<String>)

/**
 * Get a repository for the given database. This should be kept as a singleton.
 *
 * @param repositoryConfig config for the repository to be created
 */
expect inline fun <reified  T: DoorDatabase> T.asRepository(repositoryConfig: RepositoryConfig): T

expect fun <T: DoorDatabase> T.wrap(dbClass: KClass<T>): T

expect fun <T: DoorDatabase> T.unwrap(dbClass: KClass<T>): T

expect val DoorDatabase.replicationNotificationDispatcher: ReplicationNotificationDispatcher

/**
 * Add an invalidation listener for the given tables.
 */
expect fun DoorDatabase.addInvalidationListener(changeListenerRequest: ChangeListenerRequest)

/**
 * Remove the given invalidation listener
 */
expect fun DoorDatabase.removeInvalidationListener(changeListenerRequest: ChangeListenerRequest)


