package com.ustadmobile.lib.annotationprocessor.core.attachments

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository.Companion.DOOR_ATTACHMENT_URI_PREFIX
import com.ustadmobile.door.attachments.findZombieAttachments
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
import kotlin.random.Random
import com.ustadmobile.door.attachments.requireAttachmentStorageUri

/**
 * Simple tests of local storage and retrieval that are independent of the repository.
 */
@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class TestDatabaseAttachments {

    private lateinit var repDb: RepDb

    private var nodeId: Long = 0L

    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    lateinit var attachmentCatPic: File

    //From flatted nodejs library
    lateinit var attachmentStairsPic: File

    @Before
    fun setup() {
        nodeId = Random.nextLong()
        repDb = DatabaseBuilder.databaseBuilder(Any(), RepDb::class, "RepDbLocal",
                temporaryFolder.newFolder("attachments"))
            .build().also {
                it.clearAllTablesAndResetNodeId(nodeId)
            }
        attachmentCatPic = temporaryFolder.newFile().also {
            this::class.java.getResourceAsStream("/cat-pic0.jpg").writeToFile(it)
        }

        attachmentStairsPic = temporaryFolder.newFile().also{
            this::class.java.getResourceAsStream("/flatted.jpg").writeToFile(it)
        }
    }

    @Test
    fun givenAttachmentSetOnEntity_whenInsertedInDb_thenShouldBeStoredInAttachmentsDirAndRetrievable() {
        val attachmentEntity = RepEntityWithAttachment().apply {
            this.waAttachmentUri = attachmentCatPic.toDoorUri().toString()
        }

        repDb.repWithAttachmentDao.insert(attachmentEntity)

        Assert.assertTrue("Attachment stored",
            attachmentEntity.waAttachmentUri?.startsWith(DOOR_ATTACHMENT_URI_PREFIX) ?: false)

        val retrievedMd5 = runBlocking {
            repDb.retrieveAttachment(attachmentEntity.waAttachmentUri ?: "").toFile().md5Sum
        }

        Assert.assertArrayEquals("Data in stored attachment and original file is equal",
            attachmentCatPic.md5Sum, retrievedMd5)
    }

    @Test
    fun givenEntityWithAttachment_whenAttachmentUpdatedWithNewData_thenZombieDataShouldBeDeleted() {
        val repEntityWithAttachment = RepEntityWithAttachment().apply {
            waAttachmentUri = attachmentCatPic.toDoorUri().toString()
        }
        repDb.repWithAttachmentDao.insert(repEntityWithAttachment)
        val oldUri = repEntityWithAttachment.waAttachmentUri
        val oldMd5File = File(repDb.requireAttachmentStorageUri().toFile(),
            oldUri!!.substringAfter(DOOR_ATTACHMENT_URI_PREFIX))

        repEntityWithAttachment.waAttachmentUri = attachmentStairsPic.toDoorUri().toString()

        repDb.repWithAttachmentDao.update(repEntityWithAttachment)

        Thread.sleep(100)
        Assert.assertFalse("Old attachment file does not exist anymore", oldMd5File.exists())
        Assert.assertEquals("All ZombieAttachmentData entities have been deleted",
            0, runBlocking { repDb.findZombieAttachments().size })
    }

    //This test is needed because on SQLite "replace" means delete anything that conflicts, then insert.
    @Test
    fun givenEntityWithAttachment_whenAttachmentEntityReplacedWithSameMd5_thenAttachmentDataShouldNotBeDeleted() {
        val repEntityWithAttachment = RepEntityWithAttachment().apply {
            waAttachmentUri = attachmentCatPic.toDoorUri().toString()
        }
        repDb.repWithAttachmentDao.insert(repEntityWithAttachment)

        val attachmentUri = repEntityWithAttachment.waAttachmentUri
        repDb.repWithAttachmentDao.replace(repEntityWithAttachment)
        Thread.sleep(200)
        val attachmentFile = attachmentUri?.let { runBlocking { repDb.retrieveAttachment(it) } }?.toFile()
            ?: throw NullPointerException("Null attachmentFile!")
        Assert.assertTrue("Attachment file still exists after replace operation",
            attachmentFile.exists())
    }


}