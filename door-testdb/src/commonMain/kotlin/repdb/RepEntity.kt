package repdb

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable
import repdb.RepEntity.Companion.TABLE_ID

@Serializable
@Entity
@ReplicateEntity(tableId = TABLE_ID, tracker = RepEntityTracker::class)
@Trigger(name = "repent_remote_insert",
    order = Trigger.Order.INSTEAD_OF,
    on = Trigger.On.RECEIVEVIEW,
    events = [Trigger.Event.INSERT, Trigger.Event.UPDATE],
    sqlStatements = [
        """REPLACE INTO RepEntity(rePrimaryKey, reLastChangedBy, reLastChangeTime, reNumField, reString)
           VALUES (NEW.rePrimaryKey, NEW.reLastChangedBy, NEW.reLastChangeTime, NEW.reNumField, NEW.reString)
        """])
class RepEntity {

    @PrimaryKey(autoGenerate = true)
    var rePrimaryKey: Long = 0

    @LastChangedBy
    var reLastChangedBy: Long = 0

    @ReplicationVersionId
    @LastChangedTime
    var reLastChangeTime: Long = 0

    var reNumField: Int = 0

    var reString: String? = null

    companion object {
        const val TABLE_ID = 500
    }

}