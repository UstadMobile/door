package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabaseWrapper
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.ext.DoorDatabaseMetadata.Companion.SUFFIX_DOOR_METADATA
import com.ustadmobile.door.room.RoomDatabase
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

    val dbUnwrapped = if(this is DoorDatabaseWrapper<*>) {
        this.unwrap(dbClass)
    }else {
        this
    }

    val repo = repoImplClass
        .getConstructor(dbClass.java, dbClass.java, RepositoryConfig::class.java)
        .newInstance(this, dbUnwrapped, repositoryConfig)
    return repo
}

/**
 * Strip out any suffixes e.g. _ReplicateWrapper _JdbcImpl etc.
 */
private val KClass<*>.qualifiedNameBeforeLastUnderscore: String?
    get() = this.qualifiedName?.substringBeforeLast("_")

@Suppress("UNCHECKED_CAST")
actual fun <T: RoomDatabase> T.unwrap(dbClass: KClass<T>): T {
    return (this as DoorDatabaseWrapper<*>).realDatabase as T
}

