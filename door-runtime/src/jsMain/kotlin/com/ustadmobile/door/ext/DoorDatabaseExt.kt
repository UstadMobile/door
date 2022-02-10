package com.ustadmobile.door.ext

import com.ustadmobile.door.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * Get the database type that is running on the given database (DoorDbType.SQLITE Or DoorDbType.POSTGRES)
 */
actual fun DoorDatabase.dbType(): Int {
    return DoorDbType.SQLITE
}

actual fun DoorDatabase.dbSchemaVersion(): Int = this.dbVersion

/**
 * Run a transaction within a suspend coroutine context. Not really implemented at the moment.
 */
actual suspend fun <T: DoorDatabase, R> T.withDoorTransactionAsync(dbKClass: KClass<out T>, block: suspend (T) -> R) : R {
    return block(this)
}

actual fun <T: DoorDatabase, R> T.withDoorTransaction(dbKClass: KClass<T>, block: (T) -> R) : R {
    return block(this)
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
actual fun DoorDatabase.execSqlBatch(vararg sqlStatements: String) {
    throw IllegalStateException("Non-async execSqlBatch not supported on Javascript!")
}

actual fun <T : DoorDatabase> KClass<T>.doorDatabaseMetadata(): DoorDatabaseMetadata<T> {
    return DatabaseBuilder.lookupImplementations(this).metadata
}

actual fun <T : DoorDatabase> T.wrap(dbClass: KClass<T>): T {
    val jsImplClasses = DatabaseBuilder.lookupImplementations(dbClass)
    val rootDb = rootDatabase
    val wrapperKClass = jsImplClasses.replicateWrapperImplClass
        ?: throw IllegalArgumentException("$this has no replicate wrapper")
    val wrapperImpl = wrapperKClass.js.createInstance(rootDb)
    return wrapperImpl as T
}

actual fun <T : DoorDatabase> T.unwrap(dbClass: KClass<T>): T {
    return (this as? DoorDatabaseReplicateWrapper)?.realDatabase as? T
        ?: throw IllegalArgumentException("$this is not a replicate wrapper!")
}

actual inline fun <reified T : DoorDatabase> T.asRepository(repositoryConfig: RepositoryConfig): T {
    val dbClass = T::class
    val repoClass = DatabaseBuilder.lookupImplementations(dbClass).repositoryImplClass
        ?: throw IllegalArgumentException("Database ${dbClass.simpleName} does not have a repository!")
    val dbUnwrapped = if(this is DoorDatabaseReplicateWrapper) {
        this.unwrap(dbClass)
    }else {
        this
    }

    val repo: T = repoClass.js.createInstance(this, dbUnwrapped, repositoryConfig, true) as T

    Napier.d("Created JS repo $repo Node Id ${repositoryConfig.nodeId}", tag = DoorTag.LOG_TAG)
    return repo
}
