package repdb

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.ustadmobile.door.annotation.ReplicationDestinationNodeId
import com.ustadmobile.door.annotation.ReplicationEntityForeignKey
import com.ustadmobile.door.annotation.ReplicationPending
import com.ustadmobile.door.annotation.ReplicationVersionId

@Entity(primaryKeys = arrayOf("waForeignKey", "waDestination"))
class RepEntityWithAttachmentTracker {

    @ReplicationEntityForeignKey
    var waForeignKey: Long = 0

    @ReplicationVersionId
    @ColumnInfo(defaultValue = "0")
    var waTrkrVersionId: Long = 0

    @ReplicationDestinationNodeId
    var waDestination: Long = 0

    @ColumnInfo(defaultValue = "1")
    @ReplicationPending
    var waPending: Boolean = true

}