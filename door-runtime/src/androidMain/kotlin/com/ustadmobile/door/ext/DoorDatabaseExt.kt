package com.ustadmobile.door.ext

import android.net.Uri
import androidx.room.RoomDatabase
import java.lang.RuntimeException
import androidx.room.*
import com.ustadmobile.door.*
import com.ustadmobile.door.DoorDatabaseRepository.Companion.DOOR_ATTACHMENT_URI_PREFIX
import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.PreparedStatementConfig
import com.ustadmobile.door.roomjdbc.ConnectionRoomJdbc
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

private val dbVersions = mutableMapOf<Class<*>, Int>()

actual fun DoorDatabase.dbType(): Int = DoorDbType.SQLITE

actual fun DoorDatabase.dbSchemaVersion(): Int {
    val javaClass = this::class.java
    var thisVersion = dbVersions[javaClass] ?: -1
    if(thisVersion == -1) {
        val clazzName = javaClass.canonicalName!!.substringBefore('_') + "_DoorVersion"
        try {
            thisVersion = (Class.forName(clazzName).newInstance() as DoorDatabaseVersion).dbVersion
            dbVersions[javaClass] = thisVersion
        }catch(e: Exception) {
            throw RuntimeException("Could not determine schema version of ${this::class}")
        }
    }

    return thisVersion
}

actual suspend fun <T: DoorDatabase, R> T.withDoorTransactionAsync(dbKClass: KClass<out T>, block: suspend (T) -> R) : R{
    return withTransaction {
        block(this)
    }
}

actual fun <T: DoorDatabase, R> T.withDoorTransaction(dbKClass: KClass<T>, block: (T) -> R) : R {
    return runInTransaction(Callable {
        block(this)
    })
}

/**
 * The DoorDatabase
 */
fun DoorDatabase.resolveAttachmentAndroidUri(attachmentUri: String): Uri {
    val thisRepo = this as? DoorDatabaseRepository
            ?: throw IllegalArgumentException("resolveAttachmentAndroidUri must be used on the repository, not the database!")

    val attachmentsDir = thisRepo.config.attachmentsDir

    if(attachmentUri.startsWith(DOOR_ATTACHMENT_URI_PREFIX)) {
        val attachmentFile = File(File(attachmentsDir),
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
        stmt = ConnectionRoomJdbc(this).prepareStatement(
            stmtConfig.sql, stmtConfig.generatedKeys)
        return block(stmt)
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
        stmt = ConnectionRoomJdbc(this).prepareStatement(
            stmtConfig.sql, stmtConfig.generatedKeys)
        return block(stmt)
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
    invalidationTracker.refreshVersionsAsync()
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
