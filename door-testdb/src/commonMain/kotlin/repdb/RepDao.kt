package repdb

import com.ustadmobile.door.lifecycle.LiveData
import com.ustadmobile.door.annotation.DoorDao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ustadmobile.door.annotation.*
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@DoorDao
@Repository
expect interface RepDao: RepDaoInterface<RepEntity> {

    @Insert
    suspend fun insertDoorNodeAsync(node: DoorNode)

    @Insert
    fun insertDoorNode(node: DoorNode)

    @Query("""
       REPLACE INTO RepEntityTracker(trkrForeignKey, trkrDestination)
        SELECT RepEntity.rePrimaryKey AS trkrForeignKey,
               DoorNode.nodeId AS trkrDestination
          FROM ChangeLog
               JOIN RepEntity 
                    ON ChangeLog.chTableId = ${RepEntity.TABLE_ID} AND ChangeLog.chEntityPk = RepEntity.rePrimaryKey
               JOIN DoorNode ON DoorNode.nodeId = DoorNode.nodeId
         WHERE RepEntity.reLastChangeTime != COALESCE(
                (SELECT trkrVersionId
                  FROM RepEntityTracker
                 WHERE RepEntityTracker.trkrForeignKey = RepEntity.rePrimaryKey
                   AND RepEntityTracker.trkrDestination = DoorNode.nodeId), 0)
        /*psql ON CONFLICT(trkrForeignKey, trkrDestination) DO UPDATE 
              SET trkrPending = true
        */
    """)
    //Note UPDATE does not need a WHERE check - this was already checked in the insert using the where clause there
    @ReplicationRunOnChange(value = [RepEntity::class])
    @ReplicationCheckPendingNotificationsFor([RepEntity::class])
    suspend fun updateReplicationTrackers()



    @Query("""
       REPLACE INTO RepEntityTracker(trkrForeignKey, trkrDestination)
        SELECT RepEntity.rePrimaryKey AS trkrForeignKey,
               DoorNode.nodeId AS trkrDestination
          FROM RepEntity
               JOIN DoorNode ON DoorNode.nodeId = :newNodeId
         WHERE RepEntity.reLastChangeTime != COALESCE(
                (SELECT trkrVersionId
                  FROM RepEntityTracker
                 WHERE RepEntityTracker.trkrForeignKey = RepEntity.rePrimaryKey
                   AND RepEntityTracker.trkrDestination = DoorNode.nodeId), 0)
    """)
    @PostgresQuery("""
        INSERT INTO RepEntityTracker(trkrForeignKey, trkrDestination)
        SELECT RepEntity.rePrimaryKey AS trkrForeignKey,
               DoorNode.nodeId AS trkrDestination
          FROM RepEntity
               JOIN DoorNode ON DoorNode.nodeId = :newNodeId
            ON CONFLICT(trkrForeignKey, trkrDestination) DO UPDATE 
              SET trkrPending = true
    """)
    @ReplicationRunOnNewNode
    @ReplicationCheckPendingNotificationsFor([RepEntity::class])
    suspend fun updateReplicationTrackersNewNode(@NewNodeIdParam newNodeId: Long)

    @Insert
    suspend fun insertAsync(repEntity: RepEntity): Long

    @Query("""
        SELECT RepEntityTracker.*
          FROM RepEntityTracker
    """)
    fun findAllTrackers(): LiveData<List<RepEntityTracker>>

    @Insert
    fun insert(repEntity: RepEntity): Long

    @Insert
    fun insertList(repEntityList: List<RepEntity>)

    @Update
    fun update(repEntity: RepEntity)


    @Update
    suspend fun updateAsync(repEntity: RepEntity)

    @RepoHttpAccessible
    @Query("""
    SELECT COUNT(*)
      FROM RepEntity
    """)
    fun countEntities(): Int

    @Query("""
    SELECT COUNT(*)
      FROM RepEntity
    """)
    fun countEntitiesLive(): LiveData<Int>


    @Query("""
    SELECT RepEntity.*
      FROM RepEntity
     WHERE RepEntity.rePrimaryKey = :uid 
    """)
    fun findByUid(uid: Long): RepEntity?


    @Query("""
    SELECT RepEntity.*
      FROM RepEntity
     WHERE RepEntity.rePrimaryKey = :uid 
    """)
    suspend fun findByUidAsync(uid: Long): RepEntity?

    @Query("""
    SELECT RepEntity.*
      FROM RepEntity
    """)
    suspend fun findAllAsync(): List<RepEntity>


    @Query("""
    SELECT RepEntity.*
      FROM RepEntity
     WHERE RepEntity.rePrimaryKey = :uid 
    """)
    fun findByUidLive(uid: Long): LiveData<RepEntity?>

    @RepoHttpAccessible
    @Repository(Repository.METHOD_DELEGATE_TO_WEB)
    @Insert
    suspend fun insertHttp(entity: RepEntity) : Long

    @Query("""
        SELECT COALESCE(
                    (SELECT nodeClientId 
                       FROM SyncNode 
                      LIMIT 1), 0)
    """)
    suspend fun selectSyncNodeId(): Long


    @Query("""
        SELECT RepEntity.*
          FROM RepEntity
         WHERE reString IN (:strList) 
    """)
    suspend fun findInStringList(strList: List<String>): List<RepEntity>


    @Query("""SELECT MAX(:num1, :num2)""")
    @SqliteOnly
    suspend fun sqliteOnlyFun(num1: Int, num2: Int): Long

    @Query("""
        SELECT RepEntityTracker.*
          FROM RepEntityTracker
         WHERE trkrForeignKey = :pk
           AND trkrDestination = :destination
    """)
    fun findTrackerByDestinationAndPk(pk: Long, destination: Long): RepEntityTracker?

    @Query("""
        SELECT * FROM RepEntity
    """)
    fun findAllAsFlow(): Flow<List<RepEntity>>


    @Query("""
        SELECT * FROM RepEntity
    """)
    fun findAllPaged(): PagingSource<Int, RepEntity>


}