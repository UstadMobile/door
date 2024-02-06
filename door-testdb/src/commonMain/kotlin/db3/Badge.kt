package db3

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

@Entity
@ReplicateEntity(
    tableId = Badge.TABLE_ID,
    remoteInsertStrategy = ReplicateEntity.RemoteInsertStrategy.INSERT_INTO_RECEIVE_VIEW,
)
@Triggers(
    arrayOf(
        Trigger(
            name = "badge_remote_insert",
            order = Trigger.Order.INSTEAD_OF,
            on = Trigger.On.RECEIVEVIEW,
            events = [Trigger.Event.INSERT],
            sqlStatements = [
                "%UPSERT%"
            ]
        )
    )
)
@Serializable
open class Badge(
    @PrimaryKey(autoGenerate = true)
    var badgeUid: Long = 0,
    var badgeName: String? = null,
    var badgePoints: Int = 0,
    @ReplicateEtag
    @ReplicateLastModified
    var badgeLastChangeTime: Long = 0
) {

    companion object {

        const val TABLE_ID = 1702
    }
}