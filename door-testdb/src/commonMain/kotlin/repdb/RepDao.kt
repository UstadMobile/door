package repdb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ustadmobile.door.annotation.ReplicationRunOnChange
import com.ustadmobile.door.annotation.Repository
import com.ustadmobile.door.entities.DoorNode

@Dao
@Repository
abstract class RepDao {

    @Insert
    abstract suspend fun insertDoorNodeAsync(node: DoorNode)

    @Insert
    abstract fun insertDoorNode(node: DoorNode)

    @Query("""
        INSERT INTO RepEntityTracker(trkrForeignKey, trkrVersionId, trkrDestination, trkrProcessed)
        SELECT RepEntity.rePrimaryKey AS trkrForeignKey,
               RepEntity.reLastChangeTime AS trkrVersionId,
               DoorNode.nodeId AS trkrDestination,
               0 AS trkrProcessed
          FROM ChangeLog
               JOIN RepEntity 
                    ON ChangeLog.chTableId = 500 AND ChangeLog.chEntityPk = RepEntity.rePrimaryKey
               JOIN DoorNode 
                    ON DoorNode.nodeId != 0
    """)
    @ReplicationRunOnChange(value = [RepEntity::class], checkPendingReplicationsFor = [RepEntity::class])
    abstract suspend fun updateReplicationTrackers()

    @Insert
    abstract suspend fun insertAsync(repEntity: RepEntity): Long

    @Insert
    abstract fun insert(repEntity: RepEntity): Long

    @Update
    abstract fun update(repEntity: RepEntity)


    @Update
    abstract suspend fun updateAsync(repEntity: RepEntity)

    @Query("""
    SELECT COUNT(*)
      FROM RepEntity
    """)
    abstract fun countEntities(): Int


}