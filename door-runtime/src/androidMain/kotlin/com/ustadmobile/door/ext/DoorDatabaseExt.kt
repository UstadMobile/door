package com.ustadmobile.door.ext

import androidx.room.*
import com.ustadmobile.door.*
import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.roomjdbc.ConnectionRoomJdbc
import com.ustadmobile.door.util.DoorAndroidRoomHelper
import com.ustadmobile.door.util.NodeIdAuthCache
import com.ustadmobile.door.util.TransactionMode
import io.github.aakira.napier.Napier
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

actual fun RoomDatabase.dbType(): Int = DoorDbType.SQLITE

actual fun RoomDatabase.dbSchemaVersion(): Int {
    return this::class.doorDatabaseMetadata().version
}

actual suspend fun <T: RoomDatabase, R> T.withDoorTransactionAsync(
    transactionMode: TransactionMode,
    block: suspend (T) -> R
) : R{
    return rootDatabase.withTransaction {
        block(this)
    }
}

actual fun <T: RoomDatabase, R> T.withDoorTransaction(
    transactionMode: TransactionMode,
    block: (T) -> R
) : R {
    return runInTransaction(Callable {
        block(this)
    })
}


private val metadataCache = mutableMapOf<KClass<*>, DoorDatabaseMetadata<*>>()

@Suppress("UNCHECKED_CAST")
actual fun <T: RoomDatabase> KClass<T>.doorDatabaseMetadata(): DoorDatabaseMetadata<T> {
    return metadataCache.getOrPut(this) {
        Class.forName(this.java.canonicalName.substringBefore('_') + DoorDatabaseMetadata.SUFFIX_DOOR_METADATA).newInstance()
                as DoorDatabaseMetadata<*>
    } as DoorDatabaseMetadata<T>
}

actual fun RoomDatabase.execSqlBatch(vararg sqlStatements: String) {
    runInTransaction {
        sqlStatements.forEach {
            this.query(it, arrayOf())
        }
    }
}

actual suspend fun RoomDatabase.execSqlBatchAsync(vararg sqlStatements: String) {
    withTransaction {
        sqlStatements.forEach {
            this.query(it, arrayOf())
        }
    }
}

actual suspend fun <R> RoomDatabase.prepareAndUseStatementAsync(
    stmtConfig: PreparedStatementConfig,
    block: suspend (PreparedStatement) -> R
) : R {
    var stmt: PreparedStatement? = null

    try {
        stmt = ConnectionRoomJdbc(rootDatabase).prepareStatement(
            stmtConfig.sql, stmtConfig.generatedKeys)
        return block(stmt)
    }catch(e: Exception) {
        Napier.e("prepareAndUseStatement: Exception running SQL: '${stmtConfig.sql}'", e, tag = DoorTag.LOG_TAG)
        throw e
    }finally {
        stmt?.close()
    }
}

actual fun <R> RoomDatabase.prepareAndUseStatement(
    stmtConfig: PreparedStatementConfig,
    block: (PreparedStatement) -> R
) : R {
    var stmt: PreparedStatement? = null

    try {
        stmt = ConnectionRoomJdbc(rootDatabase).prepareStatement(
            stmtConfig.sql, stmtConfig.generatedKeys)
        return block(stmt)
    }catch(e: Exception) {
        Napier.e("prepareAndUseStatement: Exception running SQL: '${stmtConfig.sql}'", e, tag = DoorTag.LOG_TAG)
        throw e
    }finally {
        stmt?.close()
    }
}

actual val RoomDatabase.sourceDatabase: RoomDatabase?
    get() {
        return when {
            (this is DoorDatabaseRepository) -> this.db
            (this is DoorDatabaseWrapper<*>) -> this.realDatabase
            else -> null
        }
    }


private val pkManagersMap = ConcurrentHashMap<RoomDatabase, DoorPrimaryKeyManager>()

actual val RoomDatabase.doorPrimaryKeyManager : DoorPrimaryKeyManager
    get() {
        if(pkManagersMap[this] == null) {
            synchronized(this){
                pkManagersMap.putIfAbsent(this,
                    DoorPrimaryKeyManager(this::class.doorDatabaseMetadata().replicateEntities.keys))
            }
        }

        return pkManagersMap[this] ?: throw IllegalStateException("doorPrimaryKeyManager, What The?")
    }



//There is no alternative to the unchecked cast here. The cast is operating on generated code, so it will always
//succeed
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

@Suppress("unused")
fun <T: RoomDatabase> RoomDatabase.isWrappable(dbClass: KClass<T>): Boolean {
    try {
        Class.forName("${dbClass.qualifiedName}${DoorDatabaseWrapper.SUFFIX}")
        return true
    }catch(e: Exception) {
        return false
    }
}

@Suppress("UNCHECKED_CAST")
actual fun <T: RoomDatabase> T.unwrap(dbClass: KClass<T>): T {
    if(this is DoorDatabaseWrapper<*>) {
        return this.realDatabase as T
    }else {
        return this
    }
}

internal val RoomDatabase.dbClassName: String
    get() = this::class.qualifiedName?.substringBefore("_")
        ?: throw IllegalArgumentException("No class name!")


internal val RoomDatabase.doorAndroidRoomHelper: DoorAndroidRoomHelper
    get()  = DoorAndroidRoomHelper.lookupHelper(this)

actual val RoomDatabase.nodeIdAuthCache: NodeIdAuthCache
    get() = doorAndroidRoomHelper.nodeIdAndAuthCache

