package db3

import androidx.room.Embedded
import kotlinx.serialization.Serializable

@Serializable
@Suppress("unused")
class DiscussionPostAndPosterMember {

    @Embedded
    var discussionPost: DiscussionPost? = null

    @Embedded
    var posterMember: Member? = null

}