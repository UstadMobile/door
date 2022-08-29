package db2

import com.ustadmobile.door.annotation.DoorDao
import androidx.room.Insert

@DoorDao
abstract class ExampleLinkEntityDao {

    @Insert
    abstract fun insert(linkEntity: ExampleLinkEntity)

}