package repdb

import androidx.room.Database
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.SyncNode
import com.ustadmobile.door.entities.ChangeLog
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.entities.ReplicationStatus

@Database(version  = 1, entities = [
    RepEntity::class, RepEntityTracker::class, ChangeLog::class, DoorNode::class, ReplicationStatus::class,
    RepEntityWithAttachment::class, RepEntityWithAttachmentTracker::class, SyncNode::class,
    CompositePkEntity::class,
])
abstract class RepDb: DoorDatabase() {

    abstract val repDao: RepDao

    abstract val compositePkDao: CompositePkDao

    abstract val repWithAttachmentDao: RepWithAttachmentDao

}