package com.ustadmobile.door.attachments

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.*
import com.ustadmobile.door.ext.*
import org.mockito.kotlin.mock
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

    lateinit var db: RoomDatabase

    @Before
    fun setup() {
        attachmentPath = tempDir.newFile().absolutePath
        this::class.java.getResourceAsStream("/test-resources/cat-pic0.jpg")!!.writeToFile(File(attachmentPath))

        db = mock(extraInterfaces = arrayOf(DoorDatabaseJdbc::class)) {
            on { (this as DoorDatabaseJdbc).realAttachmentStorageUri}.thenReturn(tempDir.newFolder().toDoorUri())
        }
    }

    @Test
    fun givenValidUri_whenStored_thenCanBeRetrievedAndMd5IsSet() {
        val dummyEntity = DummyEntityWithAttachment().apply {
            attachmentUri = File(attachmentPath).toURI().toString()
        }

        runBlocking {
            db.storeAttachment(dummyEntity)
        }

        Assert.assertArrayEquals("Md5 sum assigned matches", File(attachmentPath).md5Sum,
                dummyEntity.attachmentMd5?.hexStringToByteArray())

        runBlocking {
            val storedUri = db.retrieveAttachment(dummyEntity.attachmentUri!!)
            val storedFile = storedUri.toFile()
            Assert.assertArrayEquals("Data stored is the same as data provided",
                    File(attachmentPath).md5Sum, storedFile.md5Sum)
        }
    }

}