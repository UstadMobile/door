package repdb

import androidx.room.Entity
import androidx.room.Index
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

@Serializable
@Entity(primaryKeys = arrayOf("trkrForeignKey", "trkrDestination"),
    indices = arrayOf(Index(value = arrayOf("trkrDestination", "trkrProcessed", "trkrForeignKey"))))
class RepEntityTracker {

    @ReplicationEntityForeignKey
    var trkrForeignKey: Long = 0

    @ReplicationVersionId
    var trkrVersionId: Long = 0

    @ReplicationDestinationNodeId
    var trkrDestination: Long = 0

    @ReplicationTrackerProcessed
    var trkrProcessed: Boolean = false

}