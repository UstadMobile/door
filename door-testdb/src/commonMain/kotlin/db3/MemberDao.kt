package db3

import androidx.room.Insert
import androidx.room.Query
import com.ustadmobile.door.annotation.DoorDao
import com.ustadmobile.door.annotation.Repository

@DoorDao
@Repository
expect abstract class MemberDao {

    @Insert
    abstract suspend fun insertAsync(member: Member): Long


    @Query("""
        SELECT Member.*
          FROM Member
         WHERE Member.memberUid = :memberUid 
    """)
    abstract suspend fun findByUid(memberUid: Long) : Member?

    @Query("""
        SELECT EXISTS(
               SELECT Member.memberUid
                 FROM Member
                WHERE :postUid != 0
                  AND Member.memberUid = :postUid
                  AND :nodeId != 0
        )
    """)
    abstract suspend fun checkMemberNodeHasPermission(postUid: Long, nodeId: Long): Boolean


    @Query("""
        SELECT Member.*
          FROM Member
         WHERE Member.memberUid = 
               (SELECT DiscussionPost.posterMemberUid
                  FROM DiscussionPost
                 WHERE DiscussionPost.postUid = :postUid)
    """)
    abstract suspend fun findAuthorByPostUid(postUid: Long): Member?

}