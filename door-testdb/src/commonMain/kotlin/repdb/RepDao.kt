package repdb

import androidx.room.Dao
import androidx.room.Insert

@Dao
abstract class RepDao {

    @Insert
    abstract suspend fun insertAsync(repEntity: RepEntity)

}