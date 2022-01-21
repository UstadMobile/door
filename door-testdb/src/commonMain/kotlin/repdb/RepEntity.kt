package repdb

import androidx.room.ColumnInfo
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
                       reBoolean = EXCLUDED.reBoolean,
                       reString = EXCLUDED.reString
                       */
                       
            """])))
class RepEntity {

    @PrimaryKey(autoGenerate = true)
    var rePrimaryKey: Long = 0

    @LastChangedBy
    var reLastChangedBy: Long = 0

    @ColumnInfo(defaultValue = "0")
    @ReplicationVersionId
    @LastChangedTime
    var reLastChangeTime: Long = 0

    var reNumField: Int = 0

    var reString: String? = null

    var reBoolean: Boolean = false


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as RepEntity

        if (rePrimaryKey != other.rePrimaryKey) return false
        if (reLastChangedBy != other.reLastChangedBy) return false
        if (reLastChangeTime != other.reLastChangeTime) return false
        if (reNumField != other.reNumField) return false
        if (reString != other.reString) return false
        if (reBoolean != other.reBoolean) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rePrimaryKey.hashCode()
        result = 31 * result + reLastChangedBy.hashCode()
        result = 31 * result + reLastChangeTime.hashCode()
        result = 31 * result + reNumField
        result = 31 * result + (reString?.hashCode() ?: 0)
        result = 31 * result + reBoolean.hashCode()
        return result
    }

    companion object {
        const val TABLE_ID = 500
    }


}