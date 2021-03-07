package db2

import androidx.room.Dao
import androidx.room.Query

@Dao
abstract class ExampleDaoWithInterface: ExampleDaoInterface<ExampleEntity2> {

    @Query("SELECT * FROM ExampleEntity2 WHERE uid > :param")
    abstract fun anotherQuery(param: Int): List<ExampleEntity2>

}