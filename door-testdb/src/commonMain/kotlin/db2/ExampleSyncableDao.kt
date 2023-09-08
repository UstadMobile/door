package db2

import com.ustadmobile.door.annotation.DoorDao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ustadmobile.door.SyncNode
import com.ustadmobile.door.annotation.ParamName
import com.ustadmobile.door.annotation.RepoHttpAccessible
import com.ustadmobile.door.annotation.Repository


@DoorDao
@Repository
abstract class ExampleSyncableDao {

    @Insert
    abstract fun insert(syncableEntity: ExampleSyncableEntity): Long

    @Insert
    abstract fun insertList(syncableEntityLIst: List<ExampleSyncableEntity>)

    @Insert
    abstract suspend fun insertAndReturnList(entities: List<ExampleSyncableEntity>) : List<Long>

    @Insert
    abstract suspend fun insertAsync(syncableEntity: ExampleSyncableEntity): Long

    @Query("SELECT * FROM ExampleSyncableEntity")
    abstract fun findAll(): List<ExampleSyncableEntity>

    @Query("SELECT * FROM ExampleSyncableEntity WHERE esNumber IN (:numberList)")
    abstract suspend fun findByListParam(numberList: List<Long>): List<ExampleSyncableEntity>

    @Query("SELECT * FROM ExampleSyncableEntity WHERE esUid = :uid")
    abstract fun findByUid(uid: Long): ExampleSyncableEntity?

    @Query("SELECT * FROM ExampleSyncableEntity WHERE esName = :name")
    abstract fun findByName(name: String?): ExampleSyncableEntity?

    @Query("SELECT ExampleSyncableEntity.*, OtherSyncableEntity.* FROM " +
            "ExampleSyncableEntity LEFT JOIN OtherSyncableEntity ON ExampleSyncableEntity.esUid = OtherSyncableEntity.otherFk")
    abstract fun findAllWithOtherByUid(): List<ExampleSyncableEntityWithOtherSyncableEntity>

    @Query("UPDATE ExampleSyncableEntity SET esNumber = :newNumber," +
            "esLcb = (SELECT nodeClientId FROM SyncNode LIMIT 1) " +
            "WHERE " +
            "esUid = :uid")
    abstract fun updateNumberByUid(uid: Long, newNumber: Long)

    @Update
    abstract suspend fun updateAsync(exampleSyncableEntity: ExampleSyncableEntity)


    open suspend fun findByUidAndAddOneThousand(@ParamName("uid") uid: Long): ExampleSyncableEntity? {
        val entity = findByUid(uid)
        if(entity != null)
            entity.esNumber += 1000

        return entity
    }


    //@Repository(methodType = Repository.METHOD_DELEGATE_TO_WEB)
    @Query("SELECT * FROM ExampleSyncableEntity LIMIT 1")
    //@RepoHttpAccessible
    abstract suspend fun findOneFromWeb(): ExampleSyncableEntity?

    @Query("SELECT esNumber FROM ExampleSyncableEntity LIMIT 1")
    abstract suspend fun findOneValue(): Int

    @Query("SELECT SyncNode.* FROM SyncNode LIMIT 1")
    abstract fun getSyncNode(): SyncNode?

    @Query("""
   INSERT INTO ExampleSyncableEntity (esUid, esLcsn, esMcsn, esLcb, esNumber, esName, publik)
        SELECT (esUid * 5) AS esUid, 0 AS esLcsn, 0 AS esMcsn, 0 AS esLcb, 42 AS esNumber, :name AS esName, 
               :publik AS publik 
          FROM ExampleSyncableEntity
         WHERE esName = :name
    """)
    abstract fun insertFromSelectQuery(name: String?, publik: Boolean)

}