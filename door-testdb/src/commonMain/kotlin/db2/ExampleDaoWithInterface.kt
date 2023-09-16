package db2

import com.ustadmobile.door.annotation.DoorDao
import androidx.room.Query

@DoorDao
expect abstract class ExampleDaoWithInterface: ExampleDaoInterface<ExampleEntity2> {

    @Query("SELECT * FROM ExampleEntity2 WHERE uid > :param")
    abstract fun anotherQuery(param: Int): List<ExampleEntity2>

}