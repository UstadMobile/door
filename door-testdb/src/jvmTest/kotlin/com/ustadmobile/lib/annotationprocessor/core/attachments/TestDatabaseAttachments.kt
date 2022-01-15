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

/**
 * Simple tests of local storage and retrieval that are independent of the repository.
 */
class TestDatabaseAttachments {

    private lateinit var repDb: RepDb

    private var nodeId: Long = 0L

    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    lateinit var attachmentCatPic: File

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

}