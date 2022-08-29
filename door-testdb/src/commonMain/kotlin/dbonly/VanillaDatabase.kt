package dbonly

import com.ustadmobile.door.annotation.DoorDatabase
import com.ustadmobile.door.room.RoomDatabase

@DoorDatabase(entities = [VanillaEntity::class], version = 1)
abstract class VanillaDatabase : RoomDatabase() {

    abstract val vanillaDao: VanillaDao

}