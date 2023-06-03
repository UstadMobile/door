package com.ustadmobile.door.ext

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.*
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.room.RoomJdbcImpl
import com.ustadmobile.door.util.TransactionMode
import io.github.aakira.napier.Napier
import kotlin.reflect.KClass

/**
 * Get the database type that is running on the given database (DoorDbType.SQLITE Or DoorDbType.POSTGRES)
 */
actual fun RoomDatabase.dbType(): Int {
    return DoorDbType.SQLITE
}

actual fun RoomDatabase.dbSchemaVersion(): Int = this.dbVersion

/**
 * Run a transaction within a suspend coroutine context. Not really implemented at the moment.
 */
actual suspend fun <T: RoomDatabase, R> T.withDoorTransactionAsync(
    transactionMode: TransactionMode,
    block: suspend (T) -> R
) : R {
    return (this.rootDatabase as RoomJdbcImpl).jdbcImplHelper.useConnectionAsync {
        block(this)
    }
}

actual fun <T: RoomDatabase, R> T.withDoorTransaction(
    transactionMode: TransactionMode,
    block: (T) -> R
) : R {
    throw SQLException("withDoorTransaction non-async not support on Javascript!")
}


actual fun DoorSqlDatabase.dbType(): Int {
    return DoorDbType.SQLITE
}

/**
 * Multiplatform wrapper function that will execute raw SQL statements in a
 * batch.
 *
 * Does not return any results. Will throw an exception in the event of
 * malformed SQL.
 *
 * The name deliberately lower cases sql to avoid name clashes
 */
actual fun RoomDatabase.execSqlBatch(vararg sqlStatements: String) {
    throw IllegalStateException("Non-async execSqlBatch not supported on Javascript!")
}

actual suspend fun RoomDatabase.execSqlBatchAsync(vararg sqlStatements: String) {
    execSQLBatchAsyncJs(*sqlStatements)
}

actual fun <T : RoomDatabase> KClass<T>.doorDatabaseMetadata(): DoorDatabaseMetadata<T> {
    return DatabaseBuilder.lookupImplementations(this).metadata
}

actual fun <T : RoomDatabase> T.wrap(
    dbClass: KClass<T>,
    nodeId: Long,
): T {
    val jsImplClasses = DatabaseBuilder.lookupImplementations(dbClass)
    val rootDb = rootDatabase
    val wrapperKClass = jsImplClasses.replicateWrapperImplClass
        ?: throw IllegalArgumentException("$this has no replicate wrapper")
    val wrapperImpl = wrapperKClass.js.createInstance(rootDb)
    return wrapperImpl as T
}

@Suppress("UNCHECKED_CAST")
actual fun <T : RoomDatabase> T.unwrap(dbClass: KClass<T>): T {
    return (this as? DoorDatabaseWrapper)?.realDatabase as? T
        ?: throw IllegalArgumentException("$this is not a replicate wrapper!")
}

actual inline fun <reified T : RoomDatabase> T.asRepository(repositoryConfig: RepositoryConfig): T {
    val dbClass = T::class
    val repoClass = DatabaseBuilder.lookupImplementations(dbClass).repositoryImplClass
        ?: throw IllegalArgumentException("Database ${dbClass.simpleName} does not have a repository!")
    val dbUnwrapped = if(this is DoorDatabaseWrapper) {
        this.unwrap(dbClass)
    }else {
        this
    }

    val repo: T = repoClass.js.createInstance(this, dbUnwrapped, repositoryConfig, true) as T

    Napier.d("Created JS repo $repo Node Id ${repositoryConfig.nodeId}", tag = DoorTag.LOG_TAG)
    return repo
}
