package com.ustadmobile.door.ext

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.*
import com.ustadmobile.door.ext.DoorDatabaseMetadata.Companion.SUFFIX_DOOR_METADATA
import com.ustadmobile.door.room.RoomJdbcImpl
import com.ustadmobile.door.util.TransactionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlin.reflect.KClass

actual fun RoomDatabase.dbType(): Int = (this.rootDatabase as RoomJdbcImpl).jdbcImplHelper.dbType

actual fun RoomDatabase.dbSchemaVersion(): Int = this.dbVersion

actual suspend fun <T: RoomDatabase, R> T.withDoorTransactionAsync(
    transactionMode: TransactionMode,
    block: suspend (T) -> R
): R {
    return (this.rootDatabase as RoomJdbcImpl).jdbcImplHelper.useConnectionAsync {
        block(this)
    }
}

actual fun <T: RoomDatabase, R> T.withDoorTransaction(
    transactionMode: TransactionMode,
    block: (T) -> R
): R {
    return (this.rootDatabase as RoomJdbcImpl).jdbcImplHelper.useConnection {
        block(this)
    }
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
fun <T: RoomDatabase> T.wrapDbAsRepositoryForTransaction(
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
    return repoImplKClass.java.getConstructor(
        dbKClass.java, dbKClass.java, RepositoryConfig::class.java, Boolean::class.javaPrimitiveType
    ).newInstance(wrappedDb, this, repoConfig, false) as T
}

actual fun RoomDatabase.execSqlBatch(vararg sqlStatements: String) {
    rootDatabase.execSQLBatch(*sqlStatements)
}

actual suspend fun RoomDatabase.execSqlBatchAsync(vararg sqlStatements: String) {
    withContext(Dispatchers.IO) {
        rootDatabase.execSQLBatch(*sqlStatements)
    }
}

private val metadataCache = mutableMapOf<KClass<*>, DoorDatabaseMetadata<*>>()

@Suppress("UNCHECKED_CAST")
actual fun <T: RoomDatabase> KClass<T>.doorDatabaseMetadata(): DoorDatabaseMetadata<T> {
    return metadataCache.getOrPut(this) {
        Class.forName(this.java.canonicalName.substringBefore('_') + SUFFIX_DOOR_METADATA)
            .getConstructor().newInstance()
        as DoorDatabaseMetadata<*>
    } as DoorDatabaseMetadata<T>
}


@Suppress("UNCHECKED_CAST")
actual inline fun <reified  T: RoomDatabase> T.asRepository(repositoryConfig: RepositoryConfig): T {
    val dbClass = T::class
    val repoImplClass = Class.forName("${dbClass.qualifiedName}_Repo") as Class<T>

    val dbUnwrapped = if(this is DoorDatabaseReplicateWrapper) {
        this.unwrap(dbClass)
    }else {
        this
    }

    val repo = repoImplClass
        .getConstructor(dbClass.java, dbClass.java, RepositoryConfig::class.java, Boolean::class.javaPrimitiveType)
        .newInstance(this, dbUnwrapped, repositoryConfig, true)
    return repo
}

/**
 * Strip out any suffixes e.g. _ReplicateWrapper _JdbcImpl etc.
 */
private val KClass<*>.qualifiedNameBeforeLastUnderscore: String?
    get() = this.qualifiedName?.substringBeforeLast("_")

/**
 * Wrap a syncable database to prevent accidental use of the database instead of the repo on queries
 * that modify syncable entities. All modification queries (e.g. update, insert etc) must be done on
 * the repo.
 */
@Suppress("UNCHECKED_CAST")
actual fun <T: RoomDatabase> T.wrap(dbClass: KClass<T>) : T {
    val wrapperClass = Class.forName(
        "${dbClass.qualifiedNameBeforeLastUnderscore}${DoorDatabaseReplicateWrapper.SUFFIX}"
    ) as Class<T>
    return wrapperClass.getConstructor(dbClass.java).newInstance(this)
}

@Suppress("UNCHECKED_CAST")
actual fun <T: RoomDatabase> T.unwrap(dbClass: KClass<T>): T {
    return (this as DoorDatabaseReplicateWrapper).realDatabase as T
}

