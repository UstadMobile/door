package repdb

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable
import repdb.RepEntity.Companion.TABLE_ID

@Serializable
@Entity
@ReplicateEntity(tableId = TABLE_ID, tracker = RepEntityTracker::class)
@Triggers(arrayOf(
    Trigger(name = "repent_remote_insert",
        order = Trigger.Order.INSTEAD_OF,
        on = Trigger.On.RECEIVEVIEW,
        events = [Trigger.Event.INSERT],
        sqlStatements = [
            """REPLACE INTO RepEntity(rePrimaryKey, reLastChangedBy, reLastChangeTime, reNumField, reString, reBoolean)
                       VALUES (NEW.rePrimaryKey, NEW.reLastChangedBy, NEW.reLastChangeTime, NEW.reNumField, NEW.reString, NEW.reBoolean)
                /*psql ON CONFLICT(rePrimaryKey) DO UPDATE
                   SET reLastChangedBy = EXCLUDED.reLastChangedBy,
                       reLastChangeTime = EXCLUDED.reLastChangeTime,
                       reNumField = EXCLUDED.reNumField,
                       reBoolean = EXCLUDED.reBoolean
                       */
                       
            """])))
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

    var reBoolean: Boolean = false

    companion object {
        const val TABLE_ID = 500
    }

}