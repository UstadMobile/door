package db3

import androidx.room.Embedded
import kotlinx.serialization.Serializable

@Serializable
data class DiscussionPostAndAuthorName(
    @Embedded
    var discussionPost: DiscussionPost? = null,
    var firstName: String? = null,
    var lastName: String? = null,
) {
}