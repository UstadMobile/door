package com.ustadmobile.lib.annotationprocessor.core.attachments

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository.Companion.DOOR_ATTACHMENT_URI_PREFIX
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
import com.ustadmobile.door.attachments.findZombieAttachments

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




    //This test is disabled until we introduce invalidation tracking based on the database instead of the handlechange
    // functions
    //@Test
    fun givenEntityWithAttachment_whenAttachmentUpdatedWithNewData_thenZombieDataShouldBeDeleted() {
        val repEntityWithAttachment = RepEntityWithAttachment().apply {
            waAttachmentUri = attachmentCatPic.toDoorUri().toString()
        }
        repDb.repWithAttachmentDao.insert(repEntityWithAttachment)
        val oldUri = repEntityWithAttachment.waAttachmentUri
        val oldMd5File = File(repDb.requireAttachmentStorageUri().toFile(),
            oldUri!!.substringAfter(DOOR_ATTACHMENT_URI_PREFIX))
        Assert.assertTrue("Attachment file exists after storage", oldMd5File.exists())

        repEntityWithAttachment.waAttachmentUri = attachmentStairsPic.toDoorUri().toString()

        repDb.repWithAttachmentDao.update(repEntityWithAttachment)
        val zombies = runBlocking { repDb.findZombieAttachments(RepEntityWithAttachment.TABLE_ID)}
        println(zombies)
        Assert.assertFalse("Old attachment file deleted", oldMd5File.exists())
    }


}