package repdb

import androidx.room.Database
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.entities.ChangeLog

@Database(version  = 1, entities = [RepEntity::class, ChangeLog::class])
abstract class RepDb: DoorDatabase() {

    abstract val repDao: RepDao

}