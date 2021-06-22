package com.ustadmobile.door.ext

import android.net.Uri
import androidx.room.RoomDatabase
import java.lang.RuntimeException
import androidx.room.*
import com.ustadmobile.door.*
import com.ustadmobile.door.DoorDatabaseRepository.Companion.DOOR_ATTACHMENT_URI_PREFIX
import java.io.File
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

actual suspend inline fun <T: DoorDatabase, R> T.doorWithTransaction(crossinline block: suspend(T) -> R): R {
    return withTransaction {
        block(this)
    }
}

/**
 * The DoorDatabase
 */
fun DoorDatabase.resolveAttachmentAndroidUri(attachmentUri: String): Uri {
    val thisRepo = this as? DoorDatabaseRepository
            ?: throw IllegalArgumentException("resolveAttachmentAndroidUri must be used on the repository, not the database!")

    val attachmentsDir = thisRepo.config.attachmentsDir
            ?: throw IllegalArgumentException("Repo has a null attachments directory! Cannot resolve Uris.")

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

actual fun DoorDatabase.execSql(sql: String) {
    this.query(sql, arrayOf())
}