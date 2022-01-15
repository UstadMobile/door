package com.ustadmobile.door.attachments

import android.net.Uri
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.util.systemTimeInMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import com.ustadmobile.door.attachments.requireAttachmentStorageUri
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.ext.doorAndroidRoomHelper

suspend fun DoorDatabase.filterAttachment(entityWithAttachment: EntityWithAttachment): EntityWithAttachment {
    TODO("Foo - attachmentFilter must move to the db itself")

//    val tmpDir = File.createTempFile("attachmentfilter", systemTimeInMillis().toString()).also {
//        it.delete()
//        it.mkdirs()
//    }
//
//    return config.attachmentFilters.fold(entityWithAttachment) {lastVal : EntityWithAttachment, filter->
//        filter.filter(lastVal, tmpDir.absolutePath, config.context)
//    }
}

actual suspend fun DoorDatabase.storeAttachment(entityWithAttachment: EntityWithAttachment) {
    val androidContext = doorAndroidRoomHelper.context
    if(entityWithAttachment.attachmentUri?.startsWith(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX) == true)
        return //already stored

    withContext(Dispatchers.IO) {
        val attachmentsDir = requireAttachmentStorageUri().toFile()
        attachmentsDir.takeIf { !it.exists() }?.mkdirs()

        val filteredEntity = filterAttachment(entityWithAttachment)

        //If there is no attachment data, leave it.
        val entityAttachmentUri = filteredEntity.attachmentUri
        if(entityAttachmentUri == null) {
            entityWithAttachment.attachmentMd5 = null
            entityWithAttachment.attachmentSize = 0
            return@withContext
        }

        val androidUri = Uri.parse(entityAttachmentUri)
        val inStream = androidContext.contentResolver.openInputStream(androidUri) ?: throw IOException("No input stream for $androidUri")
        val tmpDestFile = File(attachmentsDir, "${System.currentTimeMillis()}.tmp")
        val md5 = inStream.use {
            inStream.writeToFileAndGetMd5(tmpDestFile)
        }

        filteredEntity.attachmentMd5 = md5.toHexString()

        val finalDestFile = File(attachmentsDir, filteredEntity.tableNameAndMd5Path)
        finalDestFile.parentFile?.takeIf { !it.exists() }?.mkdir()
        if(!tmpDestFile.renameTo(finalDestFile)) {
            throw IOException("Unable to move attachment to correct destination")
        }

        filteredEntity.attachmentUri = entityWithAttachment.makeAttachmentUriFromTableNameAndMd5()
        filteredEntity.attachmentSize = finalDestFile.length().toInt()
    }
}

actual suspend fun DoorDatabase.retrieveAttachment(attachmentUri:  String): DoorUri {
    val file = File(requireAttachmentStorageUri().toFile(), attachmentUri
        .substringAfter(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX))
    return DoorUri(Uri.fromFile(file))
}

actual val DoorDatabase.attachmentsStorageUri: DoorUri?
    get() = doorAndroidRoomHelper.attachmentsDir?.toDoorUri()

