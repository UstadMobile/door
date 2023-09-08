package db3

import androidx.room.Query
import com.ustadmobile.door.annotation.DoorDao
import com.ustadmobile.door.annotation.RepoHttpAccessible
import com.ustadmobile.door.annotation.Repository

@DoorDao
@Repository
expect abstract class DiscussionPostDao {

    @RepoHttpAccessible()
    @Query("""
        SELECT DiscussionPost.*, Member.*
          FROM DiscussionPost
               LEFT JOIN Member
                     ON Member.memberUid = DiscussionPost.posterMemberUid 
    """)
    abstract suspend fun findAllPostersWithMembers(): List<DiscussionPostAndPosterMember>

}