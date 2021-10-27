package dbonly

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update

@Dao
abstract class VanillaDao {

    @Query("SELECT * FROM VanillaEntity WHERE vanillaUid = :pk")
    abstract fun findEntityByPk(pk: Long): VanillaEntity?

    @Update
    abstract suspend fun updateListAsync(list: List<VanillaEntity>)

}