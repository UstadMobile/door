package db2

import androidx.room.Dao
import androidx.room.Insert

@Dao
abstract class ExampleLinkEntityDao {

    @Insert
    abstract fun insert(linkEntity: ExampleLinkEntity)

}