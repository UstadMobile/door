package repdb

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.LastChangedBy
import com.ustadmobile.door.annotation.LastChangedTime
import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.door.annotation.Trigger

@Entity
@ReplicateEntity(tableId = 42, tracker = RepEntityTracker::class)
@Trigger(name = "repent_remote_insert",
    order = Trigger.Order.INSTEAD_OF,
    on = Trigger.On.RECEIVEVIEW,
    events = [Trigger.Event.INSERT, Trigger.Event.UPDATE],
    conditionSql = "SELECT (SELECT COUNT(*) FROM RepEntity) > 2",
    sqlStatements = [
        """INSERT INTO RepEntity(rePrimaryKey, reLastChangedBy, reLastChangeTime, reNumField, reString)
           VALUES (NEW.rePrimaryKey, NEW.reLastChangedBy, NEW.reLastChangeTime, NEW.reNumField, NEW.reString)
        """])
class RepEntity {

    @PrimaryKey(autoGenerate = true)
    var rePrimaryKey: Long = 0

    @LastChangedBy
    var reLastChangedBy: Long = 0

    @LastChangedTime
    var reLastChangeTime: Long = 0

    var reNumField: Int = 0

    var reString: String? = null

}