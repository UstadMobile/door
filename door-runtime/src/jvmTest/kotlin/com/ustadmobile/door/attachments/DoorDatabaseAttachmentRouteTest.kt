package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.VirtualHostScope
import com.ustadmobile.door.ext.*
import io.ktor.application.*
import io.ktor.server.testing.*
import org.junit.Test
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.routing.*
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.kodein.di.ktor.DIFeature
import org.mockito.kotlin.mock
import org.kodein.di.bind
import org.kodein.di.registerContextTranslator
import org.kodein.di.scoped
import org.kodein.di.singleton
import org.kodein.type.erased
import org.kodein.type.TypeToken
import java.io.File
import java.net.URLEncoder

class DoorDatabaseAttachmentRouteTest {

    private lateinit var mockDb: DoorDatabase

    private lateinit var scope: VirtualHostScope

    private lateinit var catPicFile: File

    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    private fun <R> withTestAttachmentRoute(testFn: TestApplicationEngine.() -> R) {
        withTestApplication({
            install(ContentNegotiation) {
                gson {
                    register(ContentType.Application.Json, GsonConverter())
                    register(ContentType.Any, GsonConverter())
                }
            }

            install(DIFeature) {
                bind<DoorDatabase>(tag = DoorTag.TAG_DB) with scoped(scope).singleton {
                    mockDb
                }

                registerContextTranslator { _: ApplicationCall -> "localhost" }
            }

            routing {
                val typeToken : TypeToken<DoorDatabase> = erased()
                doorAttachmentsRoute("attachments", typeToken)
            }
        }) {
            testFn()
        }
    }

    @Before
    fun setup() {
        mockDb =  mock(extraInterfaces = arrayOf(DoorDatabaseJdbc::class)) {
            on { (this as DoorDatabaseJdbc).realAttachmentStorageUri }
                .thenReturn(temporaryFolder.newFolder().toDoorUri())
        }

        scope = VirtualHostScope()

        catPicFile = temporaryFolder.newFile()
        this::class.java.getResourceAsStream("/test-resources/cat-pic0.jpg").writeToFile(catPicFile)

    }

    @Test
    fun givenAttachmentUploaded_whenDownloadCalled_thenDataShouldMatch() = withTestAttachmentRoute {
        val picMd5 = catPicFile.md5Sum.toHexString()
        val attachmentUri = "${DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX}Dummmy/$picMd5"
        val uploadUri = "/attachments/upload?md5=$picMd5&uri=${URLEncoder.encode(attachmentUri, "UTF-8")}"
        handleRequest(HttpMethod.Post,uploadUri) {
            setBody(catPicFile.readBytes())
        }.apply {
            Assert.assertEquals(HttpStatusCode.NoContent, this.response.status())
        }


        handleRequest(HttpMethod.Get, "/attachments/download?uri=${URLEncoder.encode(attachmentUri, "UTF-8")}") {

        }.apply {
            Assert.assertArrayEquals("Data retrieved equals data uploaded", catPicFile.readBytes(),
                response.byteContent!!)
        }
    }

}