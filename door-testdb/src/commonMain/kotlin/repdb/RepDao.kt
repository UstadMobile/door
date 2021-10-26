package repdb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ustadmobile.door.DoorLiveData
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
       REPLACE INTO RepEntityTracker(trkrForeignKey, trkrVersionId, trkrDestination)
        SELECT RepEntity.rePrimaryKey AS trkrForeignKey,
               RepEntity.reLastChangeTime AS trkrVersionId,
               DoorNode.nodeId AS trkrDestination
          FROM ChangeLog
               JOIN RepEntity 
                    ON ChangeLog.chTableId = 500 AND ChangeLog.chEntityPk = RepEntity.rePrimaryKey
               JOIN DoorNode 
                    ON DoorNode.nodeId != 0
         WHERE RepEntity.reLastChangeTime != COALESCE(
                (SELECT trkrVersionId
                  FROM RepEntityTracker
                 WHERE RepEntityTracker.trkrForeignKey = RepEntity.rePrimaryKey
                   AND RepEntityTracker.trkrDestination = DoorNode.nodeId), 0)
        /*psql ON CONFLICT(trkrForeignKey, trkrDestination) DO UPDATE 
              SET trkrProcessed = false
        */
    """)
    //Note UPDATE does not need a WHERE check - this was already checked in the insert using the where clause there
    @ReplicationRunOnChange(value = [RepEntity::class], checkPendingReplicationsFor = [RepEntity::class])
    abstract suspend fun updateReplicationTrackers()

    @Insert
    abstract suspend fun insertAsync(repEntity: RepEntity): Long

    @Query("""
        SELECT RepEntityTracker.*
          FROM RepEntityTracker
    """)
    abstract fun findAllTrackers(): DoorLiveData<List<RepEntityTracker>>

    @Insert
    abstract fun insert(repEntity: RepEntity): Long

    @Insert
    abstract fun insertList(repEntityList: List<RepEntity>)

    @Update
    abstract fun update(repEntity: RepEntity)


    @Update
    abstract suspend fun updateAsync(repEntity: RepEntity)

    @Query("""
    SELECT COUNT(*)
      FROM RepEntity
    """)
    abstract fun countEntities(): Int

    @Query("""
    SELECT RepEntity.*
      FROM RepEntity
     WHERE RepEntity.rePrimaryKey = :uid 
    """)
    abstract fun findByUid(uid: Long): RepEntity?

    @Query("""
    SELECT RepEntity.*
      FROM RepEntity
     WHERE RepEntity.rePrimaryKey = :uid 
    """)
    abstract fun findByUidLive(uid: Long): DoorLiveData<RepEntity?>

}