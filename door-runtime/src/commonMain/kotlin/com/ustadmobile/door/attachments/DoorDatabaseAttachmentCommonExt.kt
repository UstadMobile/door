package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.entities.ZombieAttachmentData
import com.ustadmobile.door.ext.mapRows
import com.ustadmobile.door.ext.prepareAndUseStatementAsync
import com.ustadmobile.door.ext.useResults
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp

fun DoorDatabase.requireAttachmentStorageUri() : DoorUri {
    return attachmentsStorageUri ?: throw IllegalStateException("Database constructed without attachment storage dir! " +
            "Please set this on the builder!")
}

suspend fun DoorDatabase.findZombieAttachments(tableId: Int): List<ZombieAttachmentData> {
    return prepareAndUseStatementAsync("""
        SELECT * 
          FROM ZombieAttachmentData
         WHERE zaTableId = ?
    """) { stmt ->
        stmt.setInt(1, tableId)

        stmt.executeQueryAsyncKmp().useResults { result ->
            result.mapRows {
                ZombieAttachmentData().apply {
                    this.zaUid = result.getLong("zaUid")
                    this.zaPrimaryKey = result.getLong("zaPrimaryKey")
                    this.zaMd5 = result.getString("zaMd5")
                    this.zaTableId = result.getInt("zaTableId")
                }
            }
        }
    }
}
