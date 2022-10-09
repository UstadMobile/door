package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.ext.copyAndGetMd5
import com.ustadmobile.door.ext.toHexString
import com.ustadmobile.door.util.systemTimeInMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Paths

/**
 * Implementation of AttachmentStorage for JVM that will simply save to/from files
 */
class FileAttachmentStorage(
    private val baseDir: File
) : AttachmentStorage {

    override suspend fun storeAttachment(entityWithAttachment: EntityWithAttachment) {
        val attachmentUri = entityWithAttachment.attachmentUri

        if(attachmentUri?.startsWith(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX) == true)
        //do nothing - attachment is already stored
            return

        if(attachmentUri == null) {
            entityWithAttachment.attachmentMd5 = null
            entityWithAttachment.attachmentSize = 0
            return
        }


        withContext(Dispatchers.IO) {
            val srcFile = Paths.get(URI(attachmentUri)).toFile()
            baseDir.takeIf { !it.exists() }?.mkdirs()

            val tmpDestFile = File(baseDir, "${systemTimeInMillis()}.tmp")
            val md5 = srcFile.copyAndGetMd5(tmpDestFile)

            val md5HexStr = md5.toHexString()
            entityWithAttachment.attachmentMd5 = md5.toHexString()
            val finalDestFile = File(baseDir, entityWithAttachment.tableNameAndMd5Path)
            finalDestFile.parentFile.takeIf { !it.exists() }?.mkdirs()

            if(!tmpDestFile.renameTo(finalDestFile)) {
                throw IOException("Could not move attachment $md5HexStr to it's final file!")
            }

            entityWithAttachment.attachmentSize = tmpDestFile.length().toInt()
            entityWithAttachment.attachmentUri = entityWithAttachment.makeAttachmentUriFromTableNameAndMd5()
        }
    }

    override suspend fun retrieveAttachment(attachmentUri: String): DoorUri {
        val file = File(baseDir, attachmentUri.substringAfter(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX))
        return DoorUri(file.toURI())
    }
}