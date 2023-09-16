package db2

import com.ustadmobile.door.annotation.DoorDao
import androidx.room.Insert
import com.ustadmobile.door.annotation.Repository

@DoorDao
@Repository
expect abstract class AccessGrantDao {

    @Insert
    abstract fun insert(entity: AccessGrant)

}