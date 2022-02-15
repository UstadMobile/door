package repdb

import androidx.room.*
import com.ustadmobile.door.DoorLiveData
import com.ustadmobile.door.annotation.*

@Dao
@Repository
abstract class RepWithAttachmentDao {

    @Query("""
       REPLACE INTO RepEntityWithAttachmentTracker(waForeignKey, waDestination)
        SELECT DISTINCT RepEntityWithAttachment.waUid AS waForeignKey,
               DoorNode.nodeId AS waDestination
          FROM ChangeLog
               JOIN RepEntityWithAttachment 
                    ON ChangeLog.chTableId = ${RepEntityWithAttachment.TABLE_ID} 
                       AND ChangeLog.chEntityPk = RepEntityWithAttachment.waUid
               JOIN DoorNode 
                    ON DoorNode.nodeId = DoorNode.nodeId
         WHERE RepEntityWithAttachment.waVersionId != COALESCE(
                (SELECT waTrkrVersionId
                  FROM RepEntityWithAttachmentTracker
                 WHERE RepEntityWithAttachmentTracker.waForeignKey = RepEntityWithAttachment.waUid
                   AND RepEntityWithAttachmentTracker.waDestination = DoorNode.nodeId), 0)
        /*psql ON CONFLICT(waForeignKey, waDestination) DO UPDATE 
              SET waPending = true
        */
    """)
    //Note UPDATE does not need a WHERE check - this was already checked in the insert using the where clause there
    @ReplicationRunOnChange(value = [RepEntityWithAttachment::class])
    @ReplicationCheckPendingNotificationsFor([RepEntityWithAttachment::class])
    abstract suspend fun updateReplicationTrackers()


    @Query("""
       REPLACE INTO RepEntityWithAttachmentTracker(waForeignKey, waDestination)
        SELECT DISTINCT RepEntityWithAttachment.waUid AS waForeignKey,
               DoorNode.nodeId AS waDestination
          FROM RepEntityWithAttachment
               JOIN DoorNode ON DoorNode.nodeId = :newNodeId
         WHERE RepEntityWithAttachment.waVersionId != COALESCE(
                (SELECT waTrkrVersionId
                  FROM RepEntityWithAttachmentTracker
                 WHERE RepEntityWithAttachmentTracker.waForeignKey = RepEntityWithAttachment.waUid
                   AND RepEntityWithAttachmentTracker.waDestination = DoorNode.nodeId), 0)
        /*psql ON CONFLICT(waForeignKey, waDestination) DO UPDATE 
              SET waPending = true
        */      
    """)
    @ReplicationRunOnNewNode
    @ReplicationCheckPendingNotificationsFor([RepEntityWithAttachment::class])
    abstract suspend fun updateReplicationTrackersNewNode(@NewNodeIdParam newNodeId: Long)


    @Insert
    abstract fun insert(entityWithAttachment: RepEntityWithAttachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun replace(entityWithAttachment: RepEntityWithAttachment)

    @Query("""
        SELECT RepEntityWithAttachment.*
          FROM RepEntityWithAttachment
         WHERE waUid = :uid
    """)
    abstract fun findByUidLive(uid: Long): DoorLiveData<RepEntityWithAttachment?>

    @Query("""
        SELECT RepEntityWithAttachment.*
          FROM RepEntityWithAttachment
         WHERE waUid = :uid
    """)
    abstract fun findByUid(uid: Long): RepEntityWithAttachment?

    @Query("""
        SELECT RepEntityWithAttachment.*
          FROM RepEntityWithAttachment
         WHERE waUid = :uid
    """)
    @RepoHttpAccessible
    @Repository(Repository.METHOD_DELEGATE_TO_WEB)
    abstract fun findByUidDelegateToWebSync(uid: Long): RepEntityWithAttachment?


    @Update
    abstract fun update(entity: RepEntityWithAttachment)

}