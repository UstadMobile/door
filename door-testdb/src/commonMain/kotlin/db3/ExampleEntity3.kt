package db3

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

@Entity
@ReplicateEntity(
    tableId = 542,
    remoteInsertStrategy = ReplicateEntity.RemoteInsertStrategy.INSERT_INTO_RECEIVE_VIEW
)

@Triggers(
    arrayOf(
        Trigger(
            name = "remote_insert3",
            order = Trigger.Order.INSTEAD_OF,
            events = arrayOf(Trigger.Event.INSERT),
            on = Trigger.On.RECEIVEVIEW,
            sqlStatements = arrayOf(
                """
                REPLACE INTO %TABLE_AND_FIELD_NAMES%
                      SELECT %NEW_VALUES%
                       WHERE %NEW_ETAG_NOT_EQUAL_TO_EXISTING%
                """
            )
        )
    )
)

@Serializable
data class ExampleEntity3(
    @PrimaryKey(autoGenerate = true)
    var eeUid: Long = 0,

    var cardNumber: Int = 0,

    var name: String? = null,

    @ReplicateLastModified
    @ReplicateEtag
    var lastUpdatedTime: Long = 0

) {

    companion object {

        const val TABLE_ID = 542
    }
}