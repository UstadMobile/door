package repdb

import androidx.room.*
import com.ustadmobile.door.annotation.DoorDao

@DoorDao
expect abstract class CompositePkDao {

    @Insert
    abstract fun insert(entity: CompositePkEntity)

    @Update
    abstract fun update(entity: CompositePkEntity)

    @Delete
    abstract fun delete(entity: CompositePkEntity)

    @Query("""
        SELECT CompositePkEntity.* 
          FROM CompositePkEntity
         WHERE CompositePkEntity.code1 = :code1 AND CompositePkEntity.code2 = :code2 
    """)
    abstract fun findByPKs(code1: Long, code2: Long): CompositePkEntity?

}