package db3

import com.ustadmobile.door.annotation.DoorDatabase
import com.ustadmobile.door.entities.OutgoingReplication
import com.ustadmobile.door.entities.PendingRepositorySession
import com.ustadmobile.door.entities.ReplicationOperation
import com.ustadmobile.door.room.RoomDatabase

@DoorDatabase(
    version = 1,
    entities = arrayOf(
        ExampleEntity3::class,
        DiscussionPost::class,
        Member::class,
        OutgoingReplication::class,
        ReplicationOperation::class,
        PendingRepositorySession::class,
        Badge::class,
        StatementEntity::class,
    )
)
expect abstract class ExampleDb3: RoomDatabase {

    abstract val exampleEntity3Dao: ExampleEntity3Dao

    abstract val discussionPostDao: DiscussionPostDao

    abstract val memberDao: MemberDao

    abstract val badgeDao: BadgeDao

    abstract val statementEntityDao: StatementEntityDao

}