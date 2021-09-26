package com.ustadmobile.door.ext

import com.ustadmobile.door.*
import com.ustadmobile.door.ext.DoorDatabaseMetadata.Companion.SUFFIX_DOOR_METADATA

import kotlin.reflect.KClass

actual fun DoorDatabase.dbType(): Int = this.jdbcDbType

actual fun DoorDatabase.dbSchemaVersion(): Int = this.dbVersion

actual suspend fun <T: DoorDatabase, R> T.withDoorTransactionAsync(dbKClass: KClass<T>, block: suspend (T) -> R): R {
    return withDoorTransactionInternalAsync(dbKClass, block)
}

actual fun <T: DoorDatabase, R> T.withDoorTransaction(dbKClass: KClass<T>, block: (T) -> R): R {
    return withDoorTransactionInternal(dbKClass, block)
}

/**
 * Where the receiver database is the transaction database created by
 * createTransactionDataSourceAndDb this is used by generated code to create a
 * new repository with the same repository config linked to the transaction
 * database.
 *
 * This is used by generated code
 *
 * @receiver transaction database created by createTransactionDataSourceAndDb
 * @param originalRepo the repository from which the repo config will be copied
 * @param dbKClass the KClass representing the database itself
 * @param repoImplKClass the KClass representing the generated repository implementation
 */
@Suppress("unused")
fun <T: DoorDatabase> T.wrapDbAsRepositoryForTransaction(
    originalRepo: T,
    dbKClass: KClass<T>,
    repoImplKClass: KClass<T>,
) : T {
    val wrappedDb = if(this::class.doorDatabaseMetadata().hasReadOnlyWrapper) {
        this.wrap(dbKClass)
    }else {
        this
    }

    val repoConfig = (originalRepo as DoorDatabaseRepository).config
    return repoImplKClass.java.getConstructor(dbKClass.java, dbKClass.java, RepositoryConfig::class.java)
        .newInstance(wrappedDb, this, repoConfig) as T
}

actual fun DoorDatabase.execSqlBatch(vararg sqlStatements: String) {
    execSQLBatch(*sqlStatements)
}

private val metadataCache = mutableMapOf<KClass<*>, DoorDatabaseMetadata<*>>()

@Suppress("UNCHECKED_CAST")
actual fun <T: DoorDatabase> KClass<T>.doorDatabaseMetadata(): DoorDatabaseMetadata<T> {
    return metadataCache.getOrPut(this) {
        Class.forName(this.java.canonicalName.substringBefore('_') + SUFFIX_DOOR_METADATA)
            .getConstructor().newInstance()
        as DoorDatabaseMetadata<*>
    } as DoorDatabaseMetadata<T>
}
