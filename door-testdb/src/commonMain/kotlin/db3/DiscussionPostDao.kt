package db3

import androidx.room.Insert
import androidx.room.Query
import app.cash.paging.PagingSource
import com.ustadmobile.door.RepositoryFlowLoadingStatusProvider
import com.ustadmobile.door.annotation.*
import kotlinx.coroutines.flow.Flow

@DoorDao
@Repository
expect abstract class DiscussionPostDao : RepositoryFlowLoadingStatusProvider {

    @Insert
    abstract suspend fun insertAsync(post: DiscussionPost): Long

    @Query("""
        SELECT DiscussionPost.*
          FROM DiscussionPost
         WHERE DiscussionPost.postUid = :postUid
    """)
    abstract suspend fun findByUid(postUid: Long): DiscussionPost?

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

    @HttpAccessible
    @Query("""
        SELECT DiscussionPost.*
          FROM DiscussionPost 
         WHERE DiscussionPost.postLastModified >= :since 
    """)
    abstract fun findAllPostAsPagingSource(since: Long): PagingSource<Int, DiscussionPost>

    @HttpAccessible(clientStrategy = HttpAccessible.ClientStrategy.HTTP_OR_THROW)
    @Query("""
        SELECT DiscussionPost.*
          FROM DiscussionPost 
         WHERE DiscussionPost.postLastModified >= :since 
    """)
    abstract fun findAllPostAsNetworkOnlyPagingSource(since: Long): PagingSource<Int, DiscussionPost>
    @HttpAccessible(clientStrategy = HttpAccessible.ClientStrategy.HTTP_WITH_FALLBACK)
    @Query("""
        SELECT DiscussionPost.*
          FROM DiscussionPost 
         WHERE DiscussionPost.postLastModified >= :since 
    """)
    abstract fun findAllPostAsNetworkWithFallbackPagingSource(since: Long): PagingSource<Int, DiscussionPost>

    @Query("""
        SELECT DiscussionPost.*
          FROM DiscussionPost
         WHERE DiscussionPost.postReplyToPostUid = :postUid 
           AND :nodeId != 0
    """)
    abstract suspend fun findAllRepliesForPost(postUid: Long, nodeId: Long): List<DiscussionPost>

    @HttpAccessible(
        pullQueriesToReplicate = arrayOf(
            HttpServerFunctionCall("findAllRepliesForPost",
                functionArgs = arrayOf(
                    HttpServerFunctionParam(
                        name = "nodeId",
                        argType = HttpServerFunctionParam.ArgType.REQUESTER_NODE_ID,
                    )
                )
            ),
            HttpServerFunctionCall("findPostAndNumReplies"),
        )
    )
    @Query("""
        SELECT DiscussionPost.*,
               (SELECT COUNT(*) 
                  FROM DiscussionPost DiscussionPostInternal
                 WHERE DiscussionPostInternal.postReplyToPostUid = :postUid) AS numReplies
         FROM DiscussionPost
        WHERE DiscussionPost.postUid = :postUid
          AND DiscussionPost.postLastModified >= :sinceTime
    """)
    abstract suspend fun findPostAndNumReplies(postUid: Long, sinceTime: Long): DiscussionPostAndNumReplies?


    @Query("""
        SELECT DiscussionPost.*
          FROM DiscussionPost
         WHERE DiscussionPost.postReplyToPostUid != 0 
    """)
    abstract fun findRootRepliesAsPagingSource(): PagingSource<Int, DiscussionPost>

    @HttpAccessible(
        pullQueriesToReplicate = arrayOf(
            HttpServerFunctionCall("findRootPostsAndNumRepliesAsPagingSource"),
            HttpServerFunctionCall("findRootRepliesAsPagingSource"),
        )
    )
    @Query("""
        SELECT DiscussionPost.*,
               (SELECT COUNT(*) 
                  FROM DiscussionPost DiscussionPostInternal
                 WHERE DiscussionPostInternal.postReplyToPostUid = DiscussionPost.postUid) AS numReplies
         FROM DiscussionPost
        WHERE DiscussionPost.postReplyToPostUid = 0
    """)
    abstract fun findRootPostsAndNumRepliesAsPagingSource(): PagingSource<Int, DiscussionPostAndNumReplies>


    @Query("""
        SELECT DiscussionPost.*
          FROM DiscussionPost
         WHERE DiscussionPost.postReplyToPostUid != 0 
         LIMIT :limit
        OFFSET :offset   
    """)
    abstract suspend fun findReplyPostsWithOffsetAndLimit(offset: Int, limit: Int): List<DiscussionPost>

    @HttpAccessible(
        pullQueriesToReplicate = arrayOf(
            HttpServerFunctionCall("findRootPostAndNumRepliesAsPagingSourceWithPagedParams"),
            HttpServerFunctionCall(
                functionName = "findReplyPostsWithOffsetAndLimit",
                functionArgs = arrayOf(
                    HttpServerFunctionParam(name = "offset", argType = HttpServerFunctionParam.ArgType.PAGING_KEY),
                    HttpServerFunctionParam(name = "limit", argType = HttpServerFunctionParam.ArgType.PAGING_LOAD_SIZE)
                )
            )
        )
    )
    @Query("""
        SELECT DiscussionPost.*,
               (SELECT COUNT(*) 
                  FROM DiscussionPost DiscussionPostInternal
                 WHERE DiscussionPostInternal.postReplyToPostUid = DiscussionPost.postUid) AS numReplies
         FROM DiscussionPost
        WHERE DiscussionPost.postReplyToPostUid = 0
    """)
    @Suppress("unused")
    abstract fun findRootPostAndNumRepliesAsPagingSourceWithPagedParams(): PagingSource<Int, DiscussionPostAndNumReplies>

    @Query("""
        SELECT DiscussionPost.*,
               (SELECT COUNT(*) 
                  FROM DiscussionPost DiscussionPostInternal
                 WHERE DiscussionPostInternal.postReplyToPostUid = DiscussionPost.postUid) AS numReplies
         FROM DiscussionPost
        WHERE DiscussionPost.postReplyToPostUid = 0
    """)
    @Suppress("unused")
    abstract fun findRootPostAndNumRepliesAsPagingSourceWithAsFlow(): Flow<List<DiscussionPostAndNumReplies>>


    @Query("""
        SELECT EXISTS(
               SELECT Member.memberUid
                 FROM Member
                WHERE :postUid != 0
                  AND Member.memberUid = :postUid
                  AND :nodeId != 0
        )
    """)
    abstract suspend fun checkNodeHasPermission(postUid: Long, nodeId: Long): Boolean

    @HttpAccessible(
        authQueries = arrayOf(
            HttpServerFunctionCall(
                functionName = "checkMemberNodeHasPermission",
                functionArgs = arrayOf(
                    HttpServerFunctionParam(
                        name = "nodeId",
                        argType = HttpServerFunctionParam.ArgType.REQUESTER_NODE_ID
                    )
                ),
                functionDao = MemberDao::class,
            )
        )
    )
    @Query("""
        SELECT DiscussionPost.*
          FROM DiscussionPost
         WHERE DiscussionPost.postReplyToPostUid = :postUid 
    """)
    abstract suspend fun findRepliesWithAuthCheck(postUid: Long): List<DiscussionPost>


    @HttpAccessible(
        pullQueriesToReplicate = arrayOf(
            HttpServerFunctionCall("getDiscussionPostAndAuthorName"),
            HttpServerFunctionCall(
                functionName = "findAuthorByPostUid",
                functionDao = MemberDao::class,
            )
        )
    )
    @Query("""
        SELECT DiscussionPost.*,
               Member.firstName AS firstName,
               Member.lastName AS lastName
          FROM DiscussionPost
               LEFT JOIN Member
                         ON Member.memberUid = DiscussionPost.posterMemberUid 
         WHERE DiscussionPost.postUid = :postUid       
    """)
    abstract suspend fun getDiscussionPostAndAuthorName(postUid: Long): DiscussionPostAndAuthorName?

}