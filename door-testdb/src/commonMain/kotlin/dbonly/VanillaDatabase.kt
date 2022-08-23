package dbonly

import com.ustadmobile.door.annotation.Database
import com.ustadmobile.door.room.RoomDatabase

@Database(entities = [VanillaEntity::class], version = 1)
abstract class VanillaDatabase : RoomDatabase() {

    abstract val vanillaDao: VanillaDao

}