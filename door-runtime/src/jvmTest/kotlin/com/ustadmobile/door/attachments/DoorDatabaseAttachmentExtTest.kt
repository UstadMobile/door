package com.ustadmobile.door.attachments

import com.nhaarman.mockitokotlin2.mock
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DummyEntityWithAttachment
import com.ustadmobile.door.ext.hexStringToByteArray
import com.ustadmobile.door.ext.md5Sum
import com.ustadmobile.door.ext.toFile
import com.ustadmobile.door.ext.writeToFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI
import java.nio.file.Paths

class DoorDatabaseAttachmentExtTest {

    @JvmField
    @Rule
    var tempDir = TemporaryFolder()

    lateinit var attachmentPath: String

    lateinit var repo: DoorDatabaseRepository

    @Before
    fun setup() {
        attachmentPath = tempDir.newFile().absolutePath
        this::class.java.getResourceAsStream("/test-resources/cat-pic0.jpg").writeToFile(File(attachmentPath))

        repo = mock {
            on {
                attachmentsDir
            }.thenReturn(tempDir.newFolder().absolutePath)

            on {
                context
            }.thenReturn(Any())
        }
    }

    @Test
    fun givenValidUri_whenStored_thenCanBeRetrievedAndMd5IsSet() {
        val dummyEntity = DummyEntityWithAttachment().apply {
            attachmentUri = File(attachmentPath).toURI().toString()
        }

        runBlocking {
            repo.storeAttachment(dummyEntity)
        }



        Assert.assertArrayEquals("Md5 sum assigned matches", File(attachmentPath).md5Sum,
                dummyEntity.attachmentMd5?.hexStringToByteArray())

        runBlocking {
            val storedUri = repo.retrieveAttachment(dummyEntity.attachmentUri!!)
            val storedFile = storedUri.toFile()
            Assert.assertArrayEquals("Data stored is the same as data provided",
                    File(attachmentPath).md5Sum, storedFile.md5Sum)
        }
    }

}