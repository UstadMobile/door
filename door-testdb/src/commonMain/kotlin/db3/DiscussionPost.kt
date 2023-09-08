package db3

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

@Entity
@ReplicateEntity(
    tableId = 543,
    remoteInsertStrategy = ReplicateEntity.RemoteInsertStrategy.INSERT_INTO_RECEIVE_VIEW
)

@Triggers(
    arrayOf(
        Trigger(
            name = "discussion_post_remote_insert",
            order = Trigger.Order.INSTEAD_OF,
            events = arrayOf(Trigger.Event.INSERT),
            on = Trigger.On.RECEIVEVIEW,
            sqlStatements = arrayOf(
                """
                REPLACE INTO DiscussionPost(postUid, postReplyToPostUid, postTitle, postText, postLastModified)
                      SELECT NEW.postUid, NEW.postReplyToPostUid, NEW.postTitle, NEW.postText, NEW.postLastModified
                       WHERE NEW.postLastModified !=
                             COALESCE((SELECT DiscussionPost_Internal.postLastModified
                                         FROM DiscussionPost DiscussionPost_Internal
                                        WHERE DiscussionPost_Internal.postUid = NEW.postUid), 0)
                """
            )
        )
    )
)
@Serializable
@Suppress("unused")
class DiscussionPost {

    @PrimaryKey(autoGenerate = true)
    var postUid: Long = 0

    var postReplyToPostUid: Long = 0

    var postTitle: String? = null

    var postText: String? = null

    @ReplicateLastModified
    @ReplicateEtag
    var postLastModified: Long = 0

    var posterMemberUid: Long = 0

}