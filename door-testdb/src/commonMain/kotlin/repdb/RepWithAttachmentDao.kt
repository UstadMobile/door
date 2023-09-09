package repdb

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ustadmobile.door.annotation.*

@DoorDao
@Repository
expect abstract class RepWithAttachmentDao {

    @Insert
    abstract fun insert(entityWithAttachment: RepEntityWithAttachment): Long

    @Insert
    abstract suspend fun insertAsync(entityWithAttachment: RepEntityWithAttachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun replace(entityWithAttachment: RepEntityWithAttachment)

    @Query("""
        SELECT RepEntityWithAttachment.*
          FROM RepEntityWithAttachment
         WHERE waUid = :uid
    """)
    abstract fun findByUid(uid: Long): RepEntityWithAttachment?

    /*
    @Query("""
        SELECT RepEntityWithAttachment.*
          FROM RepEntityWithAttachment
         WHERE waUid = :uid
    """)
    @RepoHttpAccessible
    @Repository(Repository.METHOD_DELEGATE_TO_WEB)
    abstract fun findByUidDelegateToWebSync(uid: Long): RepEntityWithAttachment?
    */

    @Update
    abstract fun update(entity: RepEntityWithAttachment)

}