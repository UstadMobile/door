package db2

import com.ustadmobile.door.annotation.Dao
import androidx.room.Insert

@Dao
abstract class ExampleLinkEntityDao {

    @Insert
    abstract fun insert(linkEntity: ExampleLinkEntity)

}