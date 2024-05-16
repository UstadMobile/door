package db3

import androidx.room.Entity
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

@Entity(
    primaryKeys = arrayOf("uidHi", "uidLo")
)
@ReplicateEntity(
    tableId = StatementEntity.TABLE_ID,
    remoteInsertStrategy = ReplicateEntity.RemoteInsertStrategy.INSERT_INTO_RECEIVE_VIEW
)

@Triggers(
    arrayOf(
        Trigger(
            name = "statement_remote_insert",
            order = Trigger.Order.INSTEAD_OF,
            events = arrayOf(Trigger.Event.INSERT),
            conditionSql = "SELECT %NEW_LAST_MODIFIED_GREATER_THAN_EXISTING%",
            on = Trigger.On.RECEIVEVIEW,
            sqlStatements = arrayOf("%UPSERT%"),
        )
    )
)
@Serializable
data class StatementEntity(
    var uidHi: Long = 0,
    var uidLo: Long = 0,
    @ReplicateLastModified
    @ReplicateEtag
    var lct: Long = 0,
    var name: String? = null,
) {
    companion object {
        const val TABLE_ID = 12121
    }
}