package com.ustadmobile.door.ext

import android.net.Uri
import androidx.room.RoomDatabase
import androidx.room.*
import com.ustadmobile.door.*
import com.ustadmobile.door.DoorDatabaseRepository.Companion.DOOR_ATTACHMENT_URI_PREFIX
import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.PreparedStatementConfig
import com.ustadmobile.door.replication.ReplicationNotificationDispatcher
import com.ustadmobile.door.roomjdbc.ConnectionRoomJdbc
import com.ustadmobile.door.util.DoorAndroidRoomHelper
import com.ustadmobile.door.util.NodeIdAuthCache
import io.github.aakira.napier.Napier
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import com.ustadmobile.door.attachments.requireAttachmentStorageUri
import com.ustadmobile.door.util.TransactionMode

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

/**
 * The DoorDatabase
 */
fun RoomDatabase.resolveAttachmentAndroidUri(attachmentUri: String): Uri {
    val attachmentsDir = requireAttachmentStorageUri().toFile()

    if(attachmentUri.startsWith(DOOR_ATTACHMENT_URI_PREFIX)) {
        val attachmentFile = File(attachmentsDir,
            attachmentUri.substringAfter(DOOR_ATTACHMENT_URI_PREFIX))

        return Uri.fromFile(attachmentFile)
    }else {
        return Uri.parse(attachmentUri)
    }
}


private val metadataCache = mutableMapOf<KClass<*>, DoorDatabaseMetadata<*>>()

@Suppress("UNCHECKED_CAST", "RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
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
            (this is DoorDatabaseReplicateWrapper) -> this.realDatabase
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
    val dbUnwrapped = if(this is DoorDatabaseReplicateWrapper) {
        this.unwrap(T::class)
    }else {
        this
    }

    val dbClass = T::class
    val repoImplClass = Class.forName("${dbClass.qualifiedName}_Repo") as Class<T>
    val repo = repoImplClass
        .getConstructor(dbClass.java, dbClass.java, RepositoryConfig::class.java, Boolean::class.javaPrimitiveType)
        .newInstance(this, dbUnwrapped, repositoryConfig, true)
    return repo
}

@Suppress("unused")
fun <T: RoomDatabase> RoomDatabase.isWrappable(dbClass: KClass<T>): Boolean {
    try {
        Class.forName("${dbClass.qualifiedName}${DoorDatabaseReplicateWrapper.SUFFIX}")
        return true
    }catch(e: Exception) {
        return false
    }
}

/**
 * Wrap a database with replication support. The wrapper manages setting primary keys, (if an entity has a field
 * annotated with @LastChangedTime) setting the last changed time as the version id, and storing attachment data.
 */
@Suppress("UNCHECKED_CAST")
actual fun <T: RoomDatabase> T.wrap(dbClass: KClass<T>) : T {
    val wrapperClass = Class.forName("${dbClass.qualifiedName}${DoorDatabaseReplicateWrapper.SUFFIX}") as Class<T>
    return wrapperClass.getConstructor(dbClass.java).newInstance(this)
}

@Suppress("UNCHECKED_CAST")
actual fun <T: RoomDatabase> T.unwrap(dbClass: KClass<T>): T {
    if(this is DoorDatabaseReplicateWrapper) {
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

actual val RoomDatabase.replicationNotificationDispatcher : ReplicationNotificationDispatcher
    get() = doorAndroidRoomHelper.replicationNotificationDispatcher

actual val RoomDatabase.nodeIdAuthCache: NodeIdAuthCache
    get() = doorAndroidRoomHelper.nodeIdAndAuthCache

actual fun RoomDatabase.addIncomingReplicationListener(incomingReplicationListener: IncomingReplicationListener) {
    doorAndroidRoomHelper.incomingReplicationListenerHelper.addIncomingReplicationListener(incomingReplicationListener)
}

actual fun RoomDatabase.removeIncomingReplicationListener(incomingReplicationListener: IncomingReplicationListener) {
    doorAndroidRoomHelper.incomingReplicationListenerHelper.removeIncomingReplicationListener(incomingReplicationListener)
}

actual val RoomDatabase.incomingReplicationListenerHelper: IncomingReplicationListenerHelper
    get() = doorAndroidRoomHelper.incomingReplicationListenerHelper

actual val RoomDatabase.rootTransactionDatabase: RoomDatabase
    get() = rootDatabase

