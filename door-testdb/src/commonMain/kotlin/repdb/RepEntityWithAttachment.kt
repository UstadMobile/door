package repdb

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable
import repdb.RepEntityWithAttachment.Companion.TABLE_ID

@Entity
@ReplicateEntity(tableId = TABLE_ID, batchSize = 5, remoteInsertStrategy = ReplicateEntity.RemoteInsertStrategy.INSERT_INTO_RECEIVE_VIEW)
@Triggers(arrayOf(
    Trigger(name = "repentwithattachment_remote_insert",
        order = Trigger.Order.INSTEAD_OF,
        on = Trigger.On.RECEIVEVIEW,
        events = [Trigger.Event.INSERT],
        sqlStatements = [
            """REPLACE INTO RepEntityWithAttachment(waUid, waVersionId, waAttachmentUri, waMd5, waSize)
                       VALUES (NEW.waUid, NEW.waVersionId, NEW.waAttachmentUri, NEW.waMd5, NEW.waSize)
                /*psql ON CONFLICT(waUid) DO UPDATE
                   SET waUid = EXCLUDED.waUid,
                       waVersionId = EXCLUDED.waVersionId,
                       waAttachmentUri = EXCLUDED.waAttachmentUri,
                       waMd5 = EXCLUDED.waMd5,
                       waSize = EXCLUDED.waSize
                       */
            """])))
@Serializable
@Suppress("unused")
class RepEntityWithAttachment {

    @PrimaryKey(autoGenerate = true)
    var waUid: Long = 0

    @ReplicateEtag
    @ReplicateLastModified
    var waVersionId: Long = 0

    @AttachmentUri
    var waAttachmentUri: String? = null

    @ColumnInfo(index = true)
    @AttachmentMd5
    var waMd5: String? = null

    @AttachmentSize
    var waSize: Int = 0

    companion object {
        const val TABLE_ID = 50
    }

}