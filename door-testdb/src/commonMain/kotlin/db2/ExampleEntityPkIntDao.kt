package db2

import com.ustadmobile.door.annotation.DoorDao
import androidx.room.Insert
import androidx.room.Query

@DoorDao
expect abstract class ExampleEntityPkIntDao {

    @Insert
    abstract fun insertAndReturnId(entity: ExampleEntityPkInt): Long

    @Insert
    abstract fun insertListAndReturnIds(entityList: List<ExampleEntityPkInt>): List<Long>

    @Query("SELECT * FROM ExampleEntityPkInt WHERE pk = :pk")
    abstract fun findByPk(pk: Int): ExampleEntityPkInt?


}