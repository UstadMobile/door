package dbonly

import com.ustadmobile.door.annotation.DoorDao
import androidx.room.Query
import androidx.room.Update

@DoorDao
abstract class VanillaDao {

    @Query("SELECT * FROM VanillaEntity WHERE vanillaUid = :pk")
    abstract fun findEntityByPk(pk: Long): VanillaEntity?

    @Update
    abstract suspend fun updateListAsync(list: List<VanillaEntity>)

}