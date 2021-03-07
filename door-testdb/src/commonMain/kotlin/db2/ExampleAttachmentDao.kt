package db2

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ustadmobile.door.annotation.Repository

@Dao
@Repository
abstract class ExampleAttachmentDao {

    @Insert
    abstract fun insert(entity: ExampleAttachmentEntity): Long

    @Update
    abstract fun update(entity: ExampleAttachmentEntity)


    @Query("SELECT * FROM ExampleAttachmentEntity WHERE eaUid = :eaUid")
    abstract fun findByUid(eaUid: Long): ExampleAttachmentEntity?


}