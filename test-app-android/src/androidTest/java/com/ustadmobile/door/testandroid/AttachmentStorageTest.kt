package com.ustadmobile.door.testandroid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.attachments.retrieveAttachment
import com.ustadmobile.door.ext.openInputStream
import com.ustadmobile.door.ext.toDoorUri
import com.ustadmobile.door.ext.writeToFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import repdb.RepDb
import repdb.RepEntityWithAttachment
import java.io.File

class AttachmentStorageTest {

    private lateinit var repDb: RepDb

    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    private lateinit var tempImgFile: File

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repDb = DatabaseBuilder.databaseBuilder(context, RepDb::class, "RepDb",
                temporaryFolder.newFolder())
            .build()
        tempImgFile = temporaryFolder.newFile()
        this::class.java.getResourceAsStream("/cat-pic0.jpg").writeToFile(tempImgFile)
    }

    @Test
    fun givenAttachmentStored_whenRetrieved_thenDataShouldBeTheSame() {
        val repEntityWithAttachment = RepEntityWithAttachment().apply {
            waAttachmentUri = tempImgFile.toDoorUri().toString()
        }
        repDb.repWithAttachmentDao.insert(repEntityWithAttachment)
        val uriRetrieved = runBlocking {
            repDb.retrieveAttachment(repEntityWithAttachment.waAttachmentUri!!)
        }

        Assert.assertArrayEquals("Contents retrieved from attachment are the same as original file",
            tempImgFile.readBytes(),
            uriRetrieved.openInputStream(ApplicationProvider.getApplicationContext())?.readBytes())

        Assert.assertTrue("Attachment was stored using door attachments",
            repEntityWithAttachment.waAttachmentUri?.startsWith(DoorDatabaseRepository.DOOR_ATTACHMENT_URI_PREFIX) ?: false)
    }

}