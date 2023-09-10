package db3

import androidx.room.Insert
import androidx.room.Query
import com.ustadmobile.door.annotation.DoorDao
import com.ustadmobile.door.annotation.RepoHttpAccessible
import com.ustadmobile.door.annotation.Repository

@DoorDao
@Repository
expect abstract class DiscussionPostDao {

    @Insert
    abstract suspend fun insertAsync(post: DiscussionPost): Long

    @RepoHttpAccessible()
    @Query("""
        SELECT DiscussionPost.*, Member.*
          FROM DiscussionPost
               LEFT JOIN Member
                     ON Member.memberUid = DiscussionPost.posterMemberUid
         WHERE DiscussionPost.postReplyToPostUid = :postUid
    """)
    abstract suspend fun findAllRepliesWithPosterMember(postUid : Long): List<DiscussionPostAndPosterMember>

    @RepoHttpAccessible()
    @Query("""
        SELECT DiscussionPost.*, Member.*
          FROM DiscussionPost
               LEFT JOIN Member
                     ON Member.memberUid = DiscussionPost.posterMemberUid
         WHERE DiscussionPost.postUid = :postUid            
    """)
    abstract suspend fun findByUidWithPosterMember(postUid: Long): DiscussionPostAndPosterMember?


    @RepoHttpAccessible()
    @Query("""
        SELECT DiscussionPost.*, Member.*
          FROM DiscussionPost
               LEFT JOIN Member
                     ON Member.memberUid = DiscussionPost.posterMemberUid
         WHERE DiscussionPost.postUid IN (:postUids)    
    """)
    abstract suspend fun findByUidList(postUids: List<Long>): List<DiscussionPostAndPosterMember>

}