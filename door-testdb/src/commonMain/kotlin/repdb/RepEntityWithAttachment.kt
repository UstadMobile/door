package repdb

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*

@Entity
@ReplicateEntity(tableId = 50, tracker = RepEntityWithAttachmentTracker::class)
class RepEntityWithAttachment {

    @PrimaryKey(autoGenerate = true)
    var waUid: Long = 0

    @ReplicationVersionId
    @LastChangedTime
    var waVersionId: Long = 0

    @AttachmentUri
    var waAttachmentUri: String? = null

    @AttachmentMd5
    var waMd5: String? = null

    @AttachmentSize
    var waSize: Int = 0

}