package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.ext.md5
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.sqljsjdbc.IndexedDb
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.url.URL

/**
 * Attachment Storage for Javascript implemented using Indexeddb. This is normally done using the same indexeddb
 * database where the sqlite data itself is saved with a different store.
 */
class IndexedDbAttachmentStorage(
    val dbName: String,
    val storeName: String = IndexedDb.ATTACHMENT_STORE_NAME,
) : AttachmentStorage{

    override suspend fun storeAttachment(entityWithAttachment: EntityWithAttachment) {
        val attachmentUri = entityWithAttachment.attachmentUri
        if(attachmentUri == null || attachmentUri.startsWith(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX))
            return

        val blob = window.fetch(entityWithAttachment.attachmentUri).await().blob().await()
        val blobMd5 = blob.md5()
        entityWithAttachment.attachmentMd5 = blobMd5
        entityWithAttachment.attachmentSize = blob.size.toInt()

        IndexedDb.storeBlob(dbName, storeName, entityWithAttachment.tableNameAndMd5Path, blob)
        entityWithAttachment.attachmentUri = entityWithAttachment.makeAttachmentUriFromTableNameAndMd5()
        URL.revokeObjectURL(attachmentUri)
    }

    override suspend fun retrieveAttachment(attachmentUri: String): DoorUri {
        val indexedDbKey = attachmentUri.substringAfter(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX)
        val blob = IndexedDb.retrieveBlob(dbName, storeName, indexedDbKey)
        val url = blob?.let { URL.createObjectURL(it) }
            ?: throw IllegalArgumentException("Attachment $attachmentUri not found in db!")
        return DoorUri(URL(url))
    }
}