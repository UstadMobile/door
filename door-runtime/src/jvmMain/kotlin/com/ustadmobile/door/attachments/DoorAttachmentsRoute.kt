package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.toFile
import com.ustadmobile.door.ext.toHexString
import com.ustadmobile.door.ext.writeToFileAndGetMd5
import io.github.aakira.napier.Napier
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kodein.di.direct
import org.kodein.di.ktor.closestDI
import org.kodein.di.ktor.di
import org.kodein.di.on
import org.kodein.type.TypeToken
import java.io.File
import java.io.IOException

/**
 * Route that handles receiving attachment uploads and serving attachment downloads
 */
fun <T: DoorDatabase> Route.doorAttachmentsRoute(path: String, typeToken: TypeToken<T>) {
    route(path) {
        post("upload") {
            val md5Param = call.parameters["md5"]
            val uriParam = call.parameters["uri"] ?: throw IllegalArgumentException("No URI")

            val db: T = call.closestDI().on(call).direct.Instance(typeToken, tag = DoorTag.TAG_DB)
            val attachmentDir = db.requireAttachmentStorageUri().toFile()

            val tmpOut = File.createTempFile("upload", "${System.currentTimeMillis()}.tmp")
            val dataMd5 = withContext(Dispatchers.IO) {
                call.receiveStream().use {
                    it.writeToFileAndGetMd5(tmpOut)
                }
            }

            if(md5Param == dataMd5.toHexString()) {
                //TODO: validate URI
                val relativePath = uriParam.substringAfter(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX)
                val finalDestFile = File(attachmentDir, relativePath)
                finalDestFile.parentFile.takeIf { !it.exists() }?.mkdirs()

                if(finalDestFile.exists()) {
                    //actually we already have it. It's fine, no need to do anything
                    tmpOut.delete()
                    Napier.d("Received attachment upload : $uriParam (already present). Deleting tmp file ",
                        tag = DoorTag.LOG_TAG)
                    call.respond(HttpStatusCode.NoContent)
                }else if(tmpOut.renameTo(finalDestFile)) {
                    call.respond(HttpStatusCode.NoContent)
                    Napier.d("Received and stored upload: $uriParam")
                }else {
                    Napier.e("Attachment upload: could not rename to final " +
                            "destination ${finalDestFile.absolutePath}", tag = DoorTag.LOG_TAG)
                    throw IOException("could not rename to final destination ${finalDestFile.absolutePath}")
                }
            }else {
                Napier.e("Attachment upload: body md5 / md5 parameter mismatch! " +
                        "Expected: $md5Param . Data MD5 = $dataMd5")
                call.respond(HttpStatusCode.BadRequest, "Body md5 does not match md5 parameter")
            }
        }

        get("download") {
            val uriParam = call.parameters["uri"] ?: throw IllegalArgumentException("No URI")

            val db: T = call.closestDI().on(call).direct.Instance(typeToken, tag = DoorTag.TAG_DB)
            val attachmentDir = db.requireAttachmentStorageUri().toFile()

            val attachmentFile = File(attachmentDir,
                    uriParam.substringAfter(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX))

            if(attachmentFile.exists()) {
                call.respondFile(attachmentFile)
            }else {
                Napier.w("Attachment TableName/MD5 not found: $uriParam")
                call.respond(HttpStatusCode.NotFound, "Not found: $uriParam")
            }
        }
    }

}
