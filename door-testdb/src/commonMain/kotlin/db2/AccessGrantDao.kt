package db2

import androidx.room.Dao
import androidx.room.Insert
import com.ustadmobile.door.annotation.Repository

@Dao
@Repository
abstract class AccessGrantDao {

    @Insert
    abstract fun insert(entity: AccessGrant)

}