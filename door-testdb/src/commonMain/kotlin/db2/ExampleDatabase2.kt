package db2

import com.ustadmobile.door.annotation.DoorDatabase
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.*
import com.ustadmobile.door.annotation.DoorNodeIdAuthRequired
import com.ustadmobile.door.annotation.MinReplicationVersion
import com.ustadmobile.door.entities.*
import db2.ExampleDatabase2.Companion.DB_VERSION

@DoorDatabase(version = DB_VERSION, entities = [ExampleEntity2::class, ExampleLinkEntity::class,
    ExampleEntityPkInt::class,
    SyncNode::class,
    ExampleSyncableEntity::class,
    OtherSyncableEntity::class,
    ChangeLog::class,
    AccessGrant::class,
    ZombieAttachmentData::class,
    DoorNode::class,
])
@MinReplicationVersion(1)
@DoorNodeIdAuthRequired
abstract class ExampleDatabase2 : RoomDatabase() {

    abstract fun exampleSyncableDao(): ExampleSyncableDao

    abstract fun exampleDao2(): ExampleDao2

    abstract fun exampleLinkedEntityDao(): ExampleLinkEntityDao

    abstract fun examlpeDaoWithInterface(): ExampleDaoWithInterface

    abstract fun exampleEntityPkIntDao(): ExampleEntityPkIntDao

    abstract fun accessGrantDao(): AccessGrantDao

    companion object {
        const val DB_VERSION = 2
    }
}