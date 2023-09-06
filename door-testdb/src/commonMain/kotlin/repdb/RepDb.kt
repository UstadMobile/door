package repdb

import com.ustadmobile.door.annotation.DoorDatabase
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.SyncNode
import com.ustadmobile.door.entities.*

@DoorDatabase(version  = 1, entities = [
    RepEntity::class,
    ChangeLog::class,
    DoorNode::class,
    ReplicationStatus::class,
    RepEntityWithAttachment::class,
    SyncNode::class,
    CompositePkEntity::class,
    OutgoingReplication::class,
])
expect abstract class RepDb: RoomDatabase {

    abstract val repDao: RepDao

    abstract val compositePkDao: CompositePkDao

    abstract val repWithAttachmentDao: RepWithAttachmentDao

}