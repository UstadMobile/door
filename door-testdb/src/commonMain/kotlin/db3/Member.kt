package db3

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

@Entity
@ReplicateEntity(
    tableId = 544,
    remoteInsertStrategy = ReplicateEntity.RemoteInsertStrategy.INSERT_INTO_RECEIVE_VIEW
)

@Triggers(
    arrayOf(
        Trigger(
            name = "member_remote_insert",
            order = Trigger.Order.INSTEAD_OF,
            events = arrayOf(Trigger.Event.INSERT),
            on = Trigger.On.RECEIVEVIEW,
            sqlStatements = arrayOf(
                """
                REPLACE INTO Member(memberUid, firstName, lastName, memberLastModified)
                      SELECT NEW.memberUid, NEW.firstName, NEW.lastName, NEW.memberLastModified
                       WHERE NEW.memberLastModified !=
                             COALESCE((SELECT Member_Internal.memberLastModified
                                         FROM Member Member_Internal
                                        WHERE Member_internal.memberUid = NEW.memberUid), 0)
                """
            )
        )
    )
)
@Serializable
@Suppress("unused")
class Member {

    @PrimaryKey(autoGenerate = true)
    var memberUid: Long = 0

    var firstName: String? = null

    var lastName: String? = null

    @ReplicateEtag
    @ReplicateLastModified
    var memberLastModified: Long = 0

}