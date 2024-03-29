package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorPrimaryKeyManager
import com.ustadmobile.door.PreparedStatementConfig
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.util.NodeIdAuthCache
import com.ustadmobile.door.util.TransactionMode
import kotlin.reflect.KClass


/**
 * Get the database type that is running on the given database (DoorDbType.SQLITE Or DoorDbType.POSTGRES)
 */
expect fun RoomDatabase.dbType(): Int

expect fun RoomDatabase.dbSchemaVersion(): Int

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
expect suspend fun <T: RoomDatabase, R> T.withDoorTransactionAsync(
    transactionMode: TransactionMode = TransactionMode.READ_WRITE,
    block: suspend (T) -> R
) : R

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
expect fun <T: RoomDatabase, R> T.withDoorTransaction(
    transactionMode: TransactionMode = TransactionMode.READ_WRITE,
    block: (T) -> R
) : R


/**
 * Multiplatform wrapper function that will execute raw SQL statements in a
 * batch.
 *
 * Does not return any results. Will throw an exception in the event of
 * malformed SQL.
 *
 * The name deliberately lower cases sql to avoid name clashes
 */
expect fun RoomDatabase.execSqlBatch(vararg sqlStatements: String)

expect suspend fun RoomDatabase.execSqlBatchAsync(vararg sqlStatements: String)

expect fun <T: RoomDatabase> KClass<T>.doorDatabaseMetadata(): DoorDatabaseMetadata<T>

/**
 * Suspended wrapper that will prepare a Statement, execute a code block, and return the code block result
 */
expect suspend fun <R> RoomDatabase.prepareAndUseStatementAsync(
    stmtConfig: PreparedStatementConfig,
    block: suspend (PreparedStatement) -> R
) : R

/**
 * Wrapper that will prepare a Statement, execute a code block, and return the code block result
 */
expect fun <R> RoomDatabase.prepareAndUseStatement(
    stmtConfig: PreparedStatementConfig,
    block: (PreparedStatement) -> R
) : R

/**
 * Sometimes we want to create a new instance of the database that is just a wrapper e.g.
 * SyncableReadOnlyWrapper, possibly a transaction wrapper. When this happens, all calls to
 * listen for changes, opening connections, etc. should be redirected to the source database
 */
expect val RoomDatabase.sourceDatabase: RoomDatabase?

expect val RoomDatabase.doorPrimaryKeyManager: DoorPrimaryKeyManager

/**
 * Get a repository for the given database. This should be kept as a singleton.
 *
 * @param repositoryConfig config for the repository to be created
 */
expect inline fun <reified  T: RoomDatabase> T.asRepository(repositoryConfig: RepositoryConfig): T

expect fun <T: RoomDatabase> T.unwrap(dbClass: KClass<T>): T

/**
 * The NodeIdAuthCache
 */
expect val RoomDatabase.nodeIdAuthCache: NodeIdAuthCache

