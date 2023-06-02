package db3

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*

@Entity
@ReplicateEntity(tableId = 542)

@Triggers(
    arrayOf(
        Trigger(
            name = "remote_insert3",
            order = Trigger.Order.INSTEAD_OF,
            events = arrayOf(Trigger.Event.INSERT),
            on = Trigger.On.RECEIVEVIEW,
            sqlStatements = arrayOf(
                """
                REPLACE INTO ExampleEntity3(eeUid, cardNumber, name, lastUpdatedTime)
                      SELECT NEW.eeUid, NEW.cardNumber, NEW.name, NEW.lastUpdatedTime
                       WHERE NEW.lastUpdatedTime !=
                             (SELECT ExampleEntity3Internal.lastUpdatedTime
                                FROM ExampleEntity3 ExampleEntity3Internal
                               WHERE ExampleEntity3Internal.eeUid = NEW.eeUid)
                """
            )
        )
    )
)
data class ExampleEntity3(
    @PrimaryKey(autoGenerate = true)
    var eeUid: Long = 0,

    var cardNumber: Int = 0,

    var name: String? = null,

    @LastChangedTime
    @ReplicationVersionId
    var lastUpdatedTime: Long = 0

) {

    companion object {

        const val TABLE_ID = 542
    }
}