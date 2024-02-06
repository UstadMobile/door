package db3

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import db3.Member.Companion.TABLE_ID
import kotlinx.serialization.Serializable

@Entity
@ReplicateEntity(
    tableId = TABLE_ID,
    remoteInsertStrategy = ReplicateEntity.RemoteInsertStrategy.INSERT_INTO_RECEIVE_VIEW
)

@Triggers(
    arrayOf(
        Trigger(
            name = "member_remote_insert",
            order = Trigger.Order.INSTEAD_OF,
            events = arrayOf(Trigger.Event.INSERT),
            on = Trigger.On.RECEIVEVIEW,
            sqlStatements = arrayOf("%UPSERT%")
        )
    )
)
@Serializable
@Suppress("unused")
data class Member(
    @PrimaryKey(autoGenerate = true)
    var memberUid: Long = 0,

    var firstName: String? = null,

    var lastName: String? = null,

    @ReplicateEtag
    @ReplicateLastModified
    var memberLastModified: Long = 0
) {

    companion object {

        const val TABLE_ID = 544

    }

}