package db2

import androidx.room.Insert
import com.ustadmobile.door.annotation.DoorDao

@DoorDao
expect abstract class ExampleLinkEntityDao {

    @Insert
    abstract fun insert(linkEntity: ExampleLinkEntity)

    @Insert
    abstract suspend fun insertAsync(linkEntity: ExampleLinkEntity)

}