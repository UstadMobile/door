package db3

import androidx.room.Insert
import androidx.room.Query
import com.ustadmobile.door.RepositoryFlowLoadingStatusProvider
import com.ustadmobile.door.annotation.DoorDao
import com.ustadmobile.door.annotation.HttpAccessible
import com.ustadmobile.door.annotation.RepoHttpBodyParam
import com.ustadmobile.door.annotation.Repository
import kotlinx.coroutines.flow.Flow

@DoorDao
@Repository
expect abstract class DiscussionPostDao : RepositoryFlowLoadingStatusProvider {

    @Insert
    abstract suspend fun insertAsync(post: DiscussionPost): Long

    @HttpAccessible()
    @Query("""
        SELECT DiscussionPost.*, Member.*
          FROM DiscussionPost
               LEFT JOIN Member
                     ON Member.memberUid = DiscussionPost.posterMemberUid
         WHERE DiscussionPost.postReplyToPostUid = :postUid
    """)
    abstract suspend fun findAllRepliesWithPosterMember(postUid : Long): List<DiscussionPostAndPosterMember>

    @HttpAccessible()
    @Query("""
        SELECT DiscussionPost.*, Member.*
          FROM DiscussionPost
               LEFT JOIN Member
                     ON Member.memberUid = DiscussionPost.posterMemberUid
         WHERE DiscussionPost.postReplyToPostUid = :postUid
    """)
    abstract fun findAllRepliesWithPosterMemberAsFlow(postUid : Long): Flow<List<DiscussionPostAndPosterMember>>


    @HttpAccessible()
    @Query("""
        SELECT DiscussionPost.*, Member.*
          FROM DiscussionPost
               LEFT JOIN Member
                     ON Member.memberUid = DiscussionPost.posterMemberUid
         WHERE DiscussionPost.postUid = :postUid            
    """)
    abstract suspend fun findByUidWithPosterMember(postUid: Long): DiscussionPostAndPosterMember?


    @HttpAccessible()
    @Query("""
        SELECT DiscussionPost.*, Member.*
          FROM DiscussionPost
               LEFT JOIN Member
                     ON Member.memberUid = DiscussionPost.posterMemberUid
         WHERE DiscussionPost.postUid = :postUid            
    """)
    abstract fun findByUidWithPosterMemberAsFlow(postUid: Long): Flow<DiscussionPostAndPosterMember?>

    @HttpAccessible(
        httpMethod = HttpAccessible.HttpMethod.POST
    )
    @Query("""
        SELECT DiscussionPost.*, Member.*
          FROM DiscussionPost
               LEFT JOIN Member
                     ON Member.memberUid = DiscussionPost.posterMemberUid
         WHERE DiscussionPost.postUid IN (:postUids)    
    """)
    abstract suspend fun findByUidList(
        @RepoHttpBodyParam postUids: List<Long>
    ): List<DiscussionPostAndPosterMember>

    @HttpAccessible(clientStrategy = HttpAccessible.ClientStrategy.HTTP_WITH_FALLBACK)
    @Query("""
        SELECT COUNT(*) 
          FROM DiscussionPost
         WHERE DiscussionPost.postLastModified >= :since 
    """)
    abstract suspend fun getNumPostsSinceTime(since: Long): Int

    @HttpAccessible(clientStrategy = HttpAccessible.ClientStrategy.HTTP_OR_THROW)
    @Query("""
        SELECT COUNT(*) 
          FROM DiscussionPost
         WHERE DiscussionPost.postLastModified >= :since 
    """)
    abstract suspend fun getNumPostsSinceTimeHttpOnly(since: Long): Int

}