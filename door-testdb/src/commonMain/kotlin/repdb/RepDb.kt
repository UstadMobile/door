package repdb

import com.ustadmobile.door.annotation.Database
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.SyncNode
import com.ustadmobile.door.entities.ChangeLog
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.entities.ReplicationStatus
import com.ustadmobile.door.entities.ZombieAttachmentData

@Database(version  = 1, entities = [
    RepEntity::class, RepEntityTracker::class, ChangeLog::class, DoorNode::class, ReplicationStatus::class,
    RepEntityWithAttachment::class, RepEntityWithAttachmentTracker::class, SyncNode::class,
    CompositePkEntity::class, ZombieAttachmentData::class
])
expect abstract class RepDb: RoomDatabase {

    abstract val repDao: RepDao

    abstract val compositePkDao: CompositePkDao

    abstract val repWithAttachmentDao: RepWithAttachmentDao

}