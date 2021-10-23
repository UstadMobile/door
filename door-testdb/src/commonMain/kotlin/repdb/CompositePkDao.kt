package repdb

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update

@Dao
abstract class CompositePkDao {

    @Insert
    abstract fun insert(entity: CompositePkEntity)

    @Update
    abstract fun update(entity: CompositePkEntity)

    @Delete
    abstract fun delete(entity: CompositePkEntity)

}