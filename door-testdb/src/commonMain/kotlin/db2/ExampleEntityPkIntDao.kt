package db2

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
abstract class ExampleEntityPkIntDao {

    @Insert
    abstract fun insertAndReturnId(entity: ExampleEntityPkInt): Int

    @Insert
    abstract fun insertListAndReturnIds(entityList: List<ExampleEntityPkInt>): List<Int>

    @Query("SELECT * FROM ExampleEntityPkInt WHERE pk = :pk")
    abstract fun findByPk(pk: Int): ExampleEntityPkInt?


}