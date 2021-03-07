package db2

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.LastChangedBy
import com.ustadmobile.door.annotation.LocalChangeSeqNum
import com.ustadmobile.door.annotation.MasterChangeSeqNum
import com.ustadmobile.door.annotation.SyncableEntity

@Entity
@SyncableEntity(tableId = 20,
    notifyOnUpdate = [
    """
        SELECT DISTINCT AccessGrant.deviceId as deviceId, ${ExampleSyncableEntity.TABLE_ID} as tableId 
        FROM ChangeLog
        JOIN AccessGrant ON ChangeLog.chTableId = 20 AND ChangeLog.chEntityPk = AccessGrant.accessId
    """,
    """
        SELECT DISTINCT AccessGrant.deviceId as deviceId, 20 as tableId 
        FROM ChangeLog
        JOIN AccessGrant ON ChangeLog.chTableId = 20 AND ChangeLog.chEntityPk = AccessGrant.accessId
    """
    ])
class AccessGrant {

    @PrimaryKey(autoGenerate = true)
    var accessId: Long = 0

    @LocalChangeSeqNum
    var aLcsn: Int = 0

    @MasterChangeSeqNum
    var aPcsn: Int = 0

    @LastChangedBy
    var aLcb: Int = 0

    var deviceId: Int = 0

    var tableId: Int = 0

    var entityUid: Long = 0

}