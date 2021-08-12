package repdb

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.ReplicationDestinationNodeId
import com.ustadmobile.door.annotation.ReplicationEntityForeignKey
import com.ustadmobile.door.annotation.ReplicationTrackerProcessed
import com.ustadmobile.door.annotation.ReplicationVersionId

@Entity
class RepEntityTracker {

    @PrimaryKey(autoGenerate = true)
    var trkrPrimaryKey: Long = 0

    @ReplicationEntityForeignKey
    var trkrForeignKey: Long = 0

    @ReplicationVersionId
    var trkrVersionId: Long = 0

    @ReplicationDestinationNodeId
    var trkrDestination: Long = 0

    @ReplicationTrackerProcessed
    var trkrProcessed: Boolean = false

}