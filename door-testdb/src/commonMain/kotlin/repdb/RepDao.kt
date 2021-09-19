package repdb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ustadmobile.door.annotation.ReplicationRunOnChange

@Dao
abstract class RepDao {

    @Query("""
        INSERT INTO RepEntityTracker(trkrForeignKey, trkrVersionId, trkrDestination, trkrProcessed)
        SELECT RepEntity.rePrimaryKey AS trkrForeignKey,
               RepEntity.reLastChangeTime AS trkrVersionId,
               DoorNode.nodeId AS trkrDestination,
               0 AS trkrProcessed
          FROM ChangeLog
               JOIN RepEntity 
                    ON ChangeLog.chTableId = 42 AND ChangeLog.chEntityPk = RepEntity.rePrimaryKey
               JOIN DoorNode 
                    ON DoorNode.nodeId != 0
    """)
    @ReplicationRunOnChange(value = [RepEntity::class], checkPendingReplicationsFor = [RepEntity::class])
    abstract suspend fun updateReplicationTrackers()

    @Insert
    abstract suspend fun insertAsync(repEntity: RepEntity)

}