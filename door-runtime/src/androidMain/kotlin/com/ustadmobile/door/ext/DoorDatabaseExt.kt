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
import com.ustadmobile.door.util.ChangeListenerRequestInvalidationObserver
import com.ustadmobile.door.util.DoorAndroidRoomHelper
import com.ustadmobile.door.util.NodeIdAuthCache
import io.github.aakira.napier.Napier
import java.io.File
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import com.ustadmobile.door.attachments.requireAttachmentStorageUri
import com.ustadmobile.door.ext.toFile

actual fun DoorDatabase.dbType(): Int = DoorDbType.SQLITE

actual fun DoorDatabase.dbSchemaVersion(): Int {
    return this::class.doorDatabaseMetadata().version
}

actual suspend fun <T: DoorDatabase, R> T.withDoorTransactionAsync(dbKClass: KClass<out T>, block: suspend (T) -> R) : R{
    return rootDatabase.withTransaction {
        block(this)
    }
}

actual fun <T: DoorDatabase, R> T.withDoorTransaction(dbKClass: KClass<T>, block: (T) -> R) : R {
    return rootDatabase.runInTransaction(Callable {
        block(this)
    })
}

/**
 * The DoorDatabase
 */
fun DoorDatabase.resolveAttachmentAndroidUri(attachmentUri: String): Uri {
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
actual fun <T: DoorDatabase> KClass<T>.doorDatabaseMetadata(): DoorDatabaseMetadata<T> {
    return metadataCache.getOrPut(this) {
        Class.forName(this.java.canonicalName.substringBefore('_') + DoorDatabaseMetadata.SUFFIX_DOOR_METADATA).newInstance()
                as DoorDatabaseMetadata<*>
    } as DoorDatabaseMetadata<T>
}

actual fun DoorDatabase.execSqlBatch(vararg sqlStatements: String) {
    runInTransaction {
        sqlStatements.forEach {
            this.query(it, arrayOf())
        }
    }
}
actual suspend fun <R> DoorDatabase.prepareAndUseStatementAsync(
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

actual fun <R> DoorDatabase.prepareAndUseStatement(
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

actual val DoorDatabase.sourceDatabase: DoorDatabase?
    get() {
        return when {
            (this is DoorDatabaseRepository) -> this.db
            (this is DoorDatabaseReplicateWrapper) -> this.realDatabase
            else -> null
        }
    }

actual fun DoorDatabase.handleTablesChanged(changeTableNames: List<String>)  {
    // When a change on Android is done within a transaction, there is no need
    // to do anything. The InvalidationTracker uses triggers, and it will find the
    // changes.
}

private val pkManagersMap = ConcurrentHashMap<RoomDatabase, DoorPrimaryKeyManager>()

actual val DoorDatabase.doorPrimaryKeyManager : DoorPrimaryKeyManager
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
actual inline fun <reified  T: DoorDatabase> T.asRepository(repositoryConfig: RepositoryConfig): T {
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
fun <T: DoorDatabase> DoorDatabase.isWrappable(dbClass: KClass<T>): Boolean {
    try {
        Class.forName("${dbClass.qualifiedName}${DoorDatabaseReplicateWrapper.SUFFIX}")
        return true
    }catch(e: Exception) {
        return false
    }
}

/**
 * Wrap a syncable database to prevent accidental use of the database instead of the repo on queries
 * that modify syncable entities. All modification queries (e.g. update, insert etc) must be done on
 * the repo.
 */
@Suppress("UNCHECKED_CAST")
actual fun <T: DoorDatabase> T.wrap(dbClass: KClass<T>) : T {
    val wrapperClass = Class.forName("${dbClass.qualifiedName}${DoorDatabaseReplicateWrapper.SUFFIX}") as Class<T>
    return wrapperClass.getConstructor(dbClass.java).newInstance(this)
}

@Suppress("UNCHECKED_CAST")
actual fun <T: DoorDatabase> T.unwrap(dbClass: KClass<T>): T {
    if(this is DoorDatabaseReplicateWrapper) {
        return this.realDatabase as T
    }else {
        return this
    }
}

actual fun DoorDatabase.addInvalidationListener(changeListenerRequest: ChangeListenerRequest) {
    invalidationTracker.addObserver(ChangeListenerRequestInvalidationObserver(changeListenerRequest))
}

actual fun DoorDatabase.removeInvalidationListener(changeListenerRequest: ChangeListenerRequest) {
    invalidationTracker.removeObserver(ChangeListenerRequestInvalidationObserver(changeListenerRequest))
}

internal val DoorDatabase.dbClassName: String
    get() = this::class.qualifiedName?.substringBefore("_")
        ?: throw IllegalArgumentException("No class name!")


internal val DoorDatabase.doorAndroidRoomHelper: DoorAndroidRoomHelper
    get()  = DoorAndroidRoomHelper.lookupHelper(this)

actual val DoorDatabase.replicationNotificationDispatcher : ReplicationNotificationDispatcher
    get() = doorAndroidRoomHelper.replicationNotificationDispatcher

actual val DoorDatabase.nodeIdAuthCache: NodeIdAuthCache
    get() = doorAndroidRoomHelper.nodeIdAndAuthCache

actual fun DoorDatabase.addIncomingReplicationListener(incomingReplicationListener: IncomingReplicationListener) {
    doorAndroidRoomHelper.incomingReplicationListenerHelper.addIncomingReplicationListener(incomingReplicationListener)
}

actual fun DoorDatabase.removeIncomingReplicationListener(incomingReplicationListener: IncomingReplicationListener) {
    doorAndroidRoomHelper.incomingReplicationListenerHelper.removeIncomingReplicationListener(incomingReplicationListener)
}

actual val DoorDatabase.incomingReplicationListenerHelper: IncomingReplicationListenerHelper
    get() = doorAndroidRoomHelper.incomingReplicationListenerHelper

actual val DoorDatabase.rootTransactionDatabase: DoorDatabase
    get() = rootDatabase

