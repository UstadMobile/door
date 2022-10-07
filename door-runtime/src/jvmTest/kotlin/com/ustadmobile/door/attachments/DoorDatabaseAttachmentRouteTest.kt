package com.ustadmobile.door.attachments

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.DoorRootDatabase
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.VirtualHostScope
import com.ustadmobile.door.ext.*
import io.ktor.client.call.*
import io.ktor.client.content.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import org.junit.Test
import io.ktor.http.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.kodein.di.bind
import org.kodein.di.ktor.di
import org.kodein.di.registerContextTranslator
import org.kodein.di.scoped
import org.kodein.di.singleton
import org.kodein.type.erased
import org.kodein.type.TypeToken
import java.io.File
import java.net.URLEncoder

class DoorDatabaseAttachmentRouteTest {

    private lateinit var mockDb: RoomDatabase

    private lateinit var scope: VirtualHostScope

    private lateinit var catPicFile: File

    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    private fun <R> testAttachmentRouteApplication(testFn: ApplicationTestBuilder.() -> R) {
        testApplication {
            application {
                di {
                    bind<RoomDatabase>(tag = DoorTag.TAG_DB) with scoped(scope).singleton {
                        mockDb
                    }

                    registerContextTranslator { _: ApplicationCall -> "localhost" }
                }

                routing {
                    val typeToken : TypeToken<RoomDatabase> = erased()
                    doorAttachmentsRoute("attachments", typeToken)
                }
            }

            testFn()
        }
    }

    @Before
    fun setup() {
        mockDb =  mock(extraInterfaces = arrayOf(DoorRootDatabase::class)) {
            on { (this as DoorRootDatabase).realAttachmentStorageUri }
                .thenReturn(temporaryFolder.newFolder().toDoorUri())
        }

        scope = VirtualHostScope()

        catPicFile = temporaryFolder.newFile()
        this::class.java.getResourceAsStream("/test-resources/cat-pic0.jpg")!!.writeToFile(catPicFile)

    }

    @Test
    fun givenAttachmentUploaded_whenDownloadCalled_thenDataShouldMatch() = testAttachmentRouteApplication {
        val picMd5 = catPicFile.md5Sum.toHexString()
        val attachmentUri = "${DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX}Dummmy/$picMd5"
        val uploadUri = "/attachments/upload?md5=$picMd5&uri=${URLEncoder.encode(attachmentUri, "UTF-8")}"

        val uploadResponse = runBlocking {
            client.post(uploadUri) {
                setBody(LocalFileContent(file = catPicFile, contentType = ContentType.Application.OctetStream))
            }
        }
        Assert.assertEquals("Upload Status returns NoContent", HttpStatusCode.NoContent,
            uploadResponse.status)

        val downloadResponse = runBlocking {
            client.get("/attachments/download?uri=${URLEncoder.encode(attachmentUri, "UTF-8")}")
        }
        val downloadResponseBytes : ByteArray = runBlocking { downloadResponse.body() }
        Assert.assertArrayEquals("Data retrieved equals data uploaded", catPicFile.readBytes(),
            downloadResponseBytes)
    }

}