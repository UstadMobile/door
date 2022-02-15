package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.entities.ZombieAttachmentData
import com.ustadmobile.door.ext.mapRows
import com.ustadmobile.door.ext.prepareAndUseStatementAsync
import com.ustadmobile.door.ext.useResults
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp
import com.ustadmobile.door.jdbc.ext.executeUpdateAsyncKmp

fun DoorDatabase.requireAttachmentStorageUri() : DoorUri {
    return attachmentsStorageUri ?: throw IllegalStateException("Database constructed without attachment storage dir! " +
            "Please set this on the builder!")
}

suspend fun DoorDatabase.findZombieAttachments(): List<ZombieAttachmentData> {
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

suspend fun DoorDatabase.deleteZombieAttachmentData(zaUids: List<Int>) {
    withDoorTransactionAsync(DoorDatabase::class) { txDb ->
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

