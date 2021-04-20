package dbonly

import androidx.room.Database
import com.ustadmobile.door.DoorDatabase

@Database(entities = [VanillaEntity::class], version = 1)
abstract class VanillaDatabase : DoorDatabase() {

    abstract val vanillaDao: VanillaDao

}