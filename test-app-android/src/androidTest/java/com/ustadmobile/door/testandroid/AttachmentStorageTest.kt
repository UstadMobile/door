package com.ustadmobile.door.testandroid

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.test.core.app.ApplicationProvider
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.attachments.retrieveAttachment
import com.ustadmobile.door.ext.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import repdb.RepDb
import repdb.RepEntityWithAttachment
import java.io.File
import com.ustadmobile.door.jdbc.ext.mapRows
import com.ustadmobile.door.jdbc.ext.useResults
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AttachmentStorageTest {

    private lateinit var repDb: RepDb

    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    private lateinit var tempImgFile: File

    private lateinit var tempImgFile2: File

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repDb = DatabaseBuilder.databaseBuilder(context, RepDb::class, "RepDb",
                temporaryFolder.newFolder())
            .build()
        tempImgFile = temporaryFolder.newFile()
        tempImgFile2 = temporaryFolder.newFile()
        this::class.java.getResourceAsStream("/cat-pic0.jpg")!!.writeToFile(tempImgFile)
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

    @Test
    fun givenAttachmentStored_whenReplacedWithNewData_thenOldAttachmentShouldBeDeleted() {
        val repEntityWithAttachment = RepEntityWithAttachment().apply {
            waAttachmentUri = tempImgFile.toDoorUri().toString()
        }
        repEntityWithAttachment.waUid = repDb.repWithAttachmentDao.insert(repEntityWithAttachment)

        val firstFileUri = repEntityWithAttachment.waAttachmentUri?.let {
            runBlocking { repDb.retrieveAttachment(it) }
        } ?: throw IllegalStateException("Could not get first file uri")

        repEntityWithAttachment.waAttachmentUri = null
        repDb.repWithAttachmentDao.update(repEntityWithAttachment)


        val latch = CountDownLatch(1)
        val invalidationObserver = object: InvalidationTracker.Observer(arrayOf("ZombieAttachmentData")) {
            override fun onInvalidated(tables: MutableSet<String>) {
                val zombieAttachmentsCount = repDb.prepareAndUseStatement("SELECT COUNT(*) FROM ZombieAttachmentData") { stmt ->
                    stmt.executeQuery().useResults { results ->
                        results.mapRows { result ->
                            result.getInt(1)
                        }
                    }
                }.first()

                if(zombieAttachmentsCount == 0)
                    latch.countDown()
            }
        }
        repDb.getInvalidationTracker().addObserver(invalidationObserver)

        latch.await(2000, TimeUnit.MILLISECONDS)

        repDb.getInvalidationTracker().removeObserver(invalidationObserver)

        Assert.assertFalse("Old file attachment was deleted", firstFileUri.toFile().exists())
    }

}