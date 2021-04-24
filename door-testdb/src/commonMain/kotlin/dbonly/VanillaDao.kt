package dbonly

import androidx.room.Dao
import androidx.room.Query

@Dao
abstract class VanillaDao {

    @Query("SELECT * FROM VanillaEntity WHERE vanillaUid = :pk")
    abstract fun findEntityByPk(pk: Long): VanillaEntity?

}