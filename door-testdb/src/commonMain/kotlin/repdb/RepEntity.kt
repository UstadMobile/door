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
        conditionSql = "SELECT NOT EXISTS(SELECT rePrimaryKey FROM RepEntity WHERE rePrimaryKey = NEW.rePrimaryKey)",
        sqlStatements = [
            """INSERT INTO RepEntity(rePrimaryKey, reLastChangedBy, reLastChangeTime, reNumField, reString, reBoolean)
               VALUES (NEW.rePrimaryKey, NEW.reLastChangedBy, NEW.reLastChangeTime, NEW.reNumField, NEW.reString, NEW.reBoolean)
            """]),
    Trigger(name = "repent_remote_update",
        order = Trigger.Order.INSTEAD_OF,
        on = Trigger.On.RECEIVEVIEW,
        events = [Trigger.Event.INSERT],
        conditionSql = "SELECT EXISTS(SELECT rePrimaryKey FROM RepEntity WHERE rePrimaryKey = NEW.rePrimaryKey)",
        sqlStatements = [
            """UPDATE RepEntity
            SET reLastChangedBy = NEW.reLastChangedBy,
                reLastChangeTime = NEW.reLastChangeTime,
                reNumField = NEW.reNumField,
                reString = NEW.reString,
                reBoolean = NEW.reBoolean
          WHERE rePrimaryKey = NEW.rePrimaryKey
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