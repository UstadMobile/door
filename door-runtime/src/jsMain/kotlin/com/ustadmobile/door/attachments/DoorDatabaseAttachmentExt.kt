package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.ext.md5
import com.ustadmobile.door.ext.rootDatabase
import com.ustadmobile.door.sqljsjdbc.IndexedDb
import com.ustadmobile.door.sqljsjdbc.IndexedDb.ATTACHMENT_STORE_NAME
import kotlinx.browser.window
import kotlinx.coroutines.await

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
    val blob = window.fetch(entityWithAttachment.attachmentUri).await().blob().await()
    val blobMd5 = blob.md5()
    entityWithAttachment.attachmentMd5 = blobMd5
    entityWithAttachment.attachmentSize = blob.size.toInt()

    val dbName = transactionRootJdbcDb.dbName
    val attachmentPath = entityWithAttachment.makeAttachmentUriFromTableNameAndMd5()
    IndexedDb.storeBlob(dbName, ATTACHMENT_STORE_NAME, attachmentPath, blob)
    entityWithAttachment.attachmentUri = attachmentPath
}

/**
 * Get a platform specific URI for the given attachment URI.
 *
 * @param attachmentUri The attachmentUri: this can be a platform dependent URI string, or it could
 *
 */
actual suspend fun DoorDatabase.retrieveAttachment(attachmentUri: String): DoorUri {
    return DoorUri.parse("http://dummyserver.com/")
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
}

actual suspend fun DoorDatabaseRepository.downloadAttachments(entityList: List<EntityWithAttachment>) {
}

actual val DoorDatabase.attachmentsStorageUri: DoorUri?
    get() = (rootDatabase as DoorDatabaseJdbc).realAttachmentStorageUri

actual val DoorDatabase.attachmentFilters: List<AttachmentFilter>
    get() = (rootDatabase as DoorDatabaseJdbc).realAttachmentFilters
