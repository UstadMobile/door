package db3

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

@Entity
@ReplicateEntity(
    tableId = DiscussionPost.TABLE_ID,
    remoteInsertStrategy = ReplicateEntity.RemoteInsertStrategy.INSERT_INTO_RECEIVE_VIEW
)

@Triggers(
    arrayOf(
        Trigger(
            name = "discussion_post_remote_insert",
            order = Trigger.Order.INSTEAD_OF,
            events = arrayOf(Trigger.Event.INSERT),
            conditionSql = "SELECT %NEW_LAST_MODIFIED_GREATER_THAN_EXISTING%",
            on = Trigger.On.RECEIVEVIEW,
            sqlStatements = arrayOf("%UPSERT%"),
        )
    )
)
@Serializable
@Suppress("unused")
data class DiscussionPost(
    @PrimaryKey(autoGenerate = true)
    var postUid: Long = 0,

    var postReplyToPostUid: Long = 0,

    var postTitle: String? = null,

    var postText: String? = null,

    @ReplicateLastModified
    @ReplicateEtag
    var postLastModified: Long = 0,

    var posterMemberUid: Long = 0,
) {

    companion object {

        const val TABLE_ID = 543

    }

}