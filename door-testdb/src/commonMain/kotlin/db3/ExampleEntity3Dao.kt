package db3

import androidx.room.Insert
import androidx.room.Query
import com.ustadmobile.door.annotation.DoorDao

@DoorDao
abstract class ExampleEntity3Dao {

    @Insert
    abstract suspend fun insertAsync(exampleEntity3: ExampleEntity3): Long

    @Query("""
        INSERT INTO OutgoingReplication(destNodeId, orTableId, orPk1, orPk2)
               SELECT :destination AS destNodeId, 
                      ${ExampleEntity3.TABLE_ID} AS orTableId,
                      :entityUid AS orPk1,
                      0 AS orPk2  
    """)
    abstract suspend fun insertOutgoingReplication(entityUid: Long, destination: Long)

    @Query("""
        SELECT ExampleEntity3.*
          FROM ExampleEntity3
         WHERE ExampleEntity3.eeUid = :uid 
    """)
    abstract suspend fun findByUid(uid: Long): ExampleEntity3?
}