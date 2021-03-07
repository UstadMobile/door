package db2

import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ustadmobile.door.DoorLiveData
import com.ustadmobile.door.SyncNode
import com.ustadmobile.door.annotation.ParamName
import com.ustadmobile.door.annotation.Repository
import com.ustadmobile.door.entities.UpdateNotification


@Dao
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

    @Query("SELECT * FROM ExampleSyncableEntity")
    abstract fun findAllLive(): DoorLiveData<List<ExampleSyncableEntity>>

    @Query("SELECT * FROM ExampleSyncableEntity")
    abstract fun findAllDataSource(): DataSource.Factory<Int, ExampleSyncableEntity>

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


    @Repository(methodType = Repository.METHOD_DELEGATE_TO_WEB)
    @Query("SELECT * FROM ExampleSyncableEntity LIMIT 1")
    abstract suspend fun findOneFromWeb(): ExampleSyncableEntity?

    @Query("SELECT esNumber FROM ExampleSyncableEntity LIMIT 1")
    abstract suspend fun findOneValue(): Int

    @Query("SELECT SyncNode.* FROM SyncNode LIMIT 1")
    abstract fun getSyncNode(): SyncNode?

    @Query("SELECT * FROM UpdateNotification")
    abstract fun findAllUpdateNotifications(): List<UpdateNotification>


}