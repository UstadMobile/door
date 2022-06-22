package com.ustadmobile.door

import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import java.io.File
import java.io.FileOutputStream


/**
 * Helper class used by generated code to receive multipart attachment upload requests.
 *
 * Requests are expected to have a FormData item called "entities" and container 1 part data section
 * for each primary key.
 *
 * The generated code must first call
 */
@Suppress("unused")
class DoorAttachmentsMultipartHelper {

    private var jsonStr: String = ""

    val attachmentTmpFiles = mutableMapOf<String, File>()

    suspend fun digestMultipart(input : MultiPartData) {
        input.forEachPart {part ->
            when(part) {
                is PartData.FormItem -> {
                    if(part.name == ENTITIES_FORM_ITEM_NAME) {
                        jsonStr = part.value
                    }
                }

                is PartData.FileItem -> {
                    part.streamProvider().use { input ->
                        val tmpOutFile = File.createTempFile("multipart", "fout")
                        val fileOutputStream = FileOutputStream(tmpOutFile)
                        fileOutputStream.use {
                            input.copyTo(fileOutputStream)
                        }
                        fileOutputStream.flush()
                        fileOutputStream.close()
                        attachmentTmpFiles[part.originalFileName!!] = tmpOutFile
                    }
                }
                else -> {
                    //do nothing
                }
            }

            part.dispose()
        }
    }

    fun moveTmpFiles(dstDir: File) {
        if(!dstDir.exists()) {
            println("Mkdirs $dstDir - result: " + dstDir.mkdirs())
        }

        attachmentTmpFiles.forEach {
            val dstFile = File(dstDir, it.key)
            if(dstFile.exists())
                dstFile.delete()

            it.value.renameTo(dstFile)
        }
    }

    fun receiveJsonStr(): String = jsonStr

    fun containsAllAttachments(pkList: List<String>) = attachmentTmpFiles.keys.all { it in pkList }

    companion object {

        const val ENTITIES_FORM_ITEM_NAME = "entities"
    }


}