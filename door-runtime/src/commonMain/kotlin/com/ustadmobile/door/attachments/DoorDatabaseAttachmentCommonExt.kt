package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorDatabaseWrapper
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.entities.ZombieAttachmentData
import com.ustadmobile.door.ext.prepareAndUseStatementAsync
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp
import com.ustadmobile.door.jdbc.ext.executeUpdateAsyncKmp
import com.ustadmobile.door.jdbc.ext.mapRows
import com.ustadmobile.door.jdbc.ext.useResults

fun RoomDatabase.requireAttachmentStorageUri() : DoorUri {
    return attachmentsStorageUri ?: throw IllegalStateException("Database constructed without attachment storage dir! " +
            "Please set this on the builder!")
}

suspend fun RoomDatabase.findZombieAttachments(): List<ZombieAttachmentData> {
    return prepareAndUseStatementAsync("""
        SELECT * 
          FROM ZombieAttachmentData
    """) { stmt ->
        stmt.executeQueryAsyncKmp().useResults { result ->
            result.mapRows {
                ZombieAttachmentData().apply {
                    this.zaUid = result.getInt("zaUid")
                    this.zaUri = result.getString("zaUri")
                }
            }
        }
    }
}

suspend fun RoomDatabase.deleteZombieAttachmentData(zaUids: List<Int>) {
    withDoorTransactionAsync { txDb ->
        txDb.prepareAndUseStatementAsync("""
            DELETE FROM ZombieAttachmentData
                  WHERE zaUid = ?
            """
        ) { stmt ->
            zaUids.forEach { zaUid ->
                stmt.setInt(1, zaUid)
                stmt.executeUpdateAsyncKmp()
            }
        }
    }
}

val RoomDatabase.attachmentStorage: AttachmentStorage?
    get() {
        val doorWrapper = (this as? DoorDatabaseWrapper)
            ?: ((this as? DoorDatabaseRepository)?.db as? DoorDatabaseWrapper)
            ?: throw IllegalArgumentException("attachmentStorage: Must only be called using the DoorWrapper or Repository!")
        return doorWrapper.attachmentStorage
    }

fun RoomDatabase.requireAttachmentStorage() = attachmentStorage
    ?: throw IllegalStateException("No attachment storage for database")
