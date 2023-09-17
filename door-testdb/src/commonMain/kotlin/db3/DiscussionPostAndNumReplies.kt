package db3

import androidx.room.Embedded
import kotlinx.serialization.Serializable

@Serializable
data class DiscussionPostAndNumReplies(
    @Embedded
    var discussionPost: DiscussionPost? = null,
    var numReplies: Int = 0,
)
