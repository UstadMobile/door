package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.toHexString
import com.ustadmobile.door.ext.writeToFileAndGetMd5
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kodein.di.direct
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

            val repo: T = call.di().on(call).direct.Instance(typeToken, tag = DoorTag.TAG_REPO)
            val attachmentDir = (repo as DoorDatabaseRepository).requireAttachmentDirFile()

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
                    call.respond(HttpStatusCode.NoContent)
                }else if(tmpOut.renameTo(finalDestFile)) {
                    call.respond(HttpStatusCode.NoContent)
                }else {
                    throw IOException("could not rename to final destination ${finalDestFile.absolutePath}")
                }
            }else {
                call.respond(HttpStatusCode.BadRequest, "Body md5 does not match md5 parameter")
            }
        }

        get("download") {
            val uriParam = call.parameters["uri"] ?: throw IllegalArgumentException("No URI")

            val repo: T = call.di().on(call).direct.Instance(typeToken, tag = DoorTag.TAG_REPO)
            val attachmentDir = (repo as DoorDatabaseRepository).requireAttachmentDirFile()

            val attachmentFile = File(attachmentDir,
                    uriParam.substringAfter(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX))

            if(attachmentFile.exists()) {
                call.respondFile(attachmentFile)
            }else {
                call.respond(HttpStatusCode.NotFound, "Not found: $uriParam")
            }
        }
    }

}
