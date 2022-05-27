package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorConstants.HEADER_DBVERSION
import com.ustadmobile.door.DoorConstants.HEADER_NODE
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.sqljsjdbc.IndexedDb
import com.ustadmobile.door.sqljsjdbc.IndexedDb.ATTACHMENT_STORE_NAME
import com.ustadmobile.door.util.encodeURIComponent
import io.github.aakira.napier.Napier
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.url.URL
import org.w3c.fetch.RequestInit
import kotlin.js.json

/**
 * Store an attachment locally. This will be called by the generated update and insert implementation.
 * The platform specific attachmentUri on the entity will be read.
 *
 * @param entityWithAttachment This interface is implemented by a generated adapter class. The
 * attachmentUri must be a readable platform specific uri.
 *
 * The attachmentUri variable will then be set to a uri in the form of:
 *
 * door-attachment://tablename/md5sum
 *
 * If the attachmentUri is already stored (e.g. it is prefixed with door-attachment://) then nothing
 * will be done
 *
 */
actual suspend fun DoorDatabase.storeAttachment(entityWithAttachment: EntityWithAttachment) {
    val attachmentUri = entityWithAttachment.attachmentUri
    if(attachmentUri == null || attachmentUri.startsWith(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX))
        return

    val blob = window.fetch(entityWithAttachment.attachmentUri).await().blob().await()
    val blobMd5 = blob.md5()
    entityWithAttachment.attachmentMd5 = blobMd5
    entityWithAttachment.attachmentSize = blob.size.toInt()

    val dbName = transactionRootJdbcDb.dbName
    IndexedDb.storeBlob(dbName, ATTACHMENT_STORE_NAME, entityWithAttachment.tableNameAndMd5Path, blob)
    entityWithAttachment.attachmentUri = entityWithAttachment.makeAttachmentUriFromTableNameAndMd5()
    URL.revokeObjectURL(attachmentUri)
}

/**
 * Get a platform specific URI for the given attachment URI.
 *
 * @param attachmentUri The attachmentUri: this can be a platform dependent URI string, or it could
 *
 */
actual suspend fun DoorDatabase.retrieveAttachment(attachmentUri: String): DoorUri {
    val indexedDbKey = attachmentUri.substringAfter(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX)
    val blob = IndexedDb.retrieveBlob(transactionRootJdbcDb.dbName, ATTACHMENT_STORE_NAME, indexedDbKey)
    val url = blob?.let { URL.createObjectURL(it) }
        ?: throw IllegalArgumentException("Attachment $attachmentUri not found in db!")
    return DoorUri(URL(url))
}

/**
 * After an update has been performed on a table that has attachments, this function is called
 * to delete old/unused data by the generated repository code
 */
actual suspend fun DoorDatabase.deleteZombieAttachments() {
}

/**
 * Upload the given attachment uri to the endpoint.
 */
actual suspend fun DoorDatabaseRepository.uploadAttachment(entityWithAttachment: EntityWithAttachment) {
    val attachmentUri = entityWithAttachment.attachmentUri
        ?: throw IllegalArgumentException("uploadAttachment: Entity with attachment uri must not be null")
    val attachmentMd5 = entityWithAttachment.attachmentMd5
        ?: throw IllegalArgumentException("uploadAttachment: Entity attachment must not be null")

    val indexedDbKey = attachmentUri.substringAfter(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX)
    val dbName = (this as DoorDatabase).transactionRootJdbcDb.dbName
    val blob = IndexedDb.retrieveBlob(dbName, ATTACHMENT_STORE_NAME, indexedDbKey)
        ?: throw IllegalStateException("No blob found for $attachmentUri")
    val params = "md5=${encodeURIComponent(attachmentMd5)}&uri=${encodeURIComponent(attachmentUri)}"
    try {
        val headers = json(HEADER_DBVERSION to db.dbSchemaVersion().toString(),
            HEADER_NODE to "${config.nodeId}/${config.auth}")
        window.fetch("${config.endpoint}attachments/upload?$params",
            RequestInit(method = "POST", body = blob, headers = headers)).await()
    }catch(e: Exception) {
        Napier.e(tag = DoorTag.LOG_TAG) { "Exception uploading attachment $attachmentUri" }
        throw e
    }
}

actual suspend fun DoorDatabaseRepository.downloadAttachments(entityList: List<EntityWithAttachment>) {



}

actual val DoorDatabase.attachmentsStorageUri: DoorUri?
    get() = (rootDatabase as DoorDatabaseJdbc).realAttachmentStorageUri

actual val DoorDatabase.attachmentFilters: List<AttachmentFilter>
    get() = (rootDatabase as DoorDatabaseJdbc).realAttachmentFilters
