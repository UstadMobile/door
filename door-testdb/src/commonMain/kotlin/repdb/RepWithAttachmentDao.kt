package repdb

import androidx.room.Dao
import androidx.room.Insert
import com.ustadmobile.door.annotation.Repository

@Dao
@Repository
abstract class RepWithAttachmentDao {

    @Insert
    abstract fun insert(entityWithAttachment: RepEntityWithAttachment)

}