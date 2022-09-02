package dbonly

import androidx.room.Insert
import com.ustadmobile.door.annotation.DoorDao
import androidx.room.Query
import androidx.room.Update

@DoorDao
expect abstract class VanillaDao {

    @Query("SELECT * FROM VanillaEntity WHERE vanillaUid = :pk")
    abstract fun findEntityByPk(pk: Long): VanillaEntity?

    @Insert
    abstract fun insert(entity: VanillaEntity)

    @Update
    abstract suspend fun updateListAsync(list: List<VanillaEntity>)

}