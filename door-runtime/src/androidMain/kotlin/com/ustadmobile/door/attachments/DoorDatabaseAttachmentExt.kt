package com.ustadmobile.door.attachments

import android.content.Context
import android.net.Uri
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.ext.toHexString
import com.ustadmobile.door.ext.writeToFileAndGetMd5
import com.ustadmobile.door.util.systemTimeInMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

suspend fun DoorDatabaseRepository.filterAttachment(entityWithAttachment: EntityWithAttachment): EntityWithAttachment {
    val tmpDir = File.createTempFile("attachmentfilter", systemTimeInMillis().toString()).also {
        it.delete()
        it.mkdirs()
    }

    return attachmentFilters.fold(entityWithAttachment) {lastVal : EntityWithAttachment, filter->
        filter.filter(lastVal, tmpDir.absolutePath, context)
    }
}

actual suspend fun DoorDatabaseRepository.storeAttachment(entityWithAttachment: EntityWithAttachment) {
    val androidContext = context as Context
    if(entityWithAttachment.attachmentUri?.startsWith(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX) == true)
        return //already stored

    withContext(Dispatchers.IO) {
        val attachmentsDir = requireAttachmentDirFile()
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
        val md5 = inStream.writeToFileAndGetMd5(tmpDestFile)
        inStream.close()
        filteredEntity.attachmentMd5 = md5.toHexString()

        val finalDestFile = File(requireAttachmentDirFile(), filteredEntity.tableNameAndMd5Path)
        finalDestFile.parentFile?.takeIf { !it.exists() }?.mkdir()
        if(!tmpDestFile.renameTo(finalDestFile)) {
            throw IOException("Unable to move attachment to correct destination")
        }

        filteredEntity.attachmentUri = entityWithAttachment.makeAttachmentUriFromTableNameAndMd5()
        filteredEntity.attachmentSize = finalDestFile.length().toInt()
    }
}

actual suspend fun DoorDatabaseRepository.retrieveAttachment(attachmentUri:  String): DoorUri {
    val file = File(requireAttachmentDirFile(), attachmentUri.substringAfter("door-attachment://"))
    return DoorUri(Uri.fromFile(file))
}

