package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.ext.dbSchemaVersion
import com.ustadmobile.door.ext.doorNodeAndVersionHeaders
import com.ustadmobile.door.ext.writeToFile
import io.ktor.client.request.*
import io.ktor.http.*
import java.io.File
import java.net.URL
import io.ktor.client.content.LocalFileContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.net.HttpURLConnection


fun DoorDatabaseRepository.requireAttachmentDirFile(): File {
    return File(config.attachmentsDir)
}

/**
 * Upload the given attachment uri to the endpoint.
 */
@Suppress("BlockingMethodInNonBlockingContext")
actual suspend fun DoorDatabaseRepository.uploadAttachment(entityWithAttachment: EntityWithAttachment) {
    val attachmentUri = entityWithAttachment.attachmentUri
            ?: throw IllegalArgumentException("uploadAttachment: Entity with attachment uri must not be null")
    val attachmentMd5 = entityWithAttachment.attachmentMd5
            ?: throw IllegalArgumentException("uploadAttachment: Entity attachment must not be null")

    val attachmentFile = File(requireAttachmentDirFile(),
        attachmentUri.substringAfter(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX))
    val endpointUrl = URL(URL(config.endpoint), "$dbPath/attachments/upload")

    //val inputFile = Paths.get(systemUri).toFile()
    config.httpClient.post<Unit>(endpointUrl.toString()) {
        doorNodeAndVersionHeaders(this@uploadAttachment)
        parameter("md5", attachmentMd5)
        parameter("uri", attachmentUri)

        body = LocalFileContent(file = attachmentFile, contentType = ContentType.Application.OctetStream)
    }
}

actual suspend fun DoorDatabaseRepository.downloadAttachments(entityList: List<EntityWithAttachment>) {
    val entitiesWithAttachmentData = entityList.mapNotNull { it.attachmentUri }
    if(entitiesWithAttachmentData.isEmpty())
        return

    withContext(Dispatchers.IO) {
        entitiesWithAttachmentData.forEach { attachmentUri ->
            val destPath = attachmentUri.substringAfter(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX)
            val destFile = File(requireAttachmentDirFile(), destPath)

            if(!destFile.exists()) {
                val url = URL(URL(config.endpoint),
                        "$dbPath/attachments/download?uri=${URLEncoder.encode(attachmentUri, "UTF-8")}")

                destFile.parentFile.takeIf { !it.exists() }?.mkdirs()

                val urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.setRequestProperty(DoorConstants.HEADER_DBVERSION,
                        db.dbSchemaVersion().toString())
                urlConnection.setRequestProperty(DoorConstants.HEADER_NODE,
                    "${this@downloadAttachments.config.nodeId}/${this@downloadAttachments.config.auth}")
                urlConnection.inputStream.writeToFile(destFile)
            }
        }
    }
}

actual suspend fun DoorDatabaseRepository.deleteZombieAttachments(entityWithAttachment: EntityWithAttachment) {
    //TODO: transaction support for this, rework to use replicateentities instead.
    /*
    val syncRepo = this as? DoorDatabaseSyncRepository ?: throw IllegalStateException("Database hosting attachments must be syncable")
    val zombieAttachmentDataList = syncRepo.syncHelperEntitiesDao.findZombieAttachments(
            entityWithAttachment.tableName, 0)

    zombieAttachmentDataList.forEach {
        val attachmentFile = File(requireAttachmentDirFile(), it.tableNameAndMd5Path)
        attachmentFile.delete()
    }

    syncRepo.syncHelperEntitiesDao.deleteZombieAttachments(zombieAttachmentDataList)
     */
}
