package db2

import androidx.paging.DataSource
import androidx.room.*
import com.ustadmobile.door.*

@Dao
abstract class ExampleDao2 {

    @Insert
    abstract fun insertAndReturnId(entity: ExampleEntity2): Long

    @Insert
    abstract suspend fun insertAsync(entity: ExampleEntity2)

    @Insert
    abstract suspend fun insertAsyncAndGiveId(entity: ExampleEntity2) : Long

    @Insert
    abstract fun insertList(entityList: List<ExampleEntity2>)

    @Insert
    abstract suspend fun insertListAsync(entityList: List<ExampleEntity2>)

    @Insert
    abstract fun insertOtherList(entityList: List<ExampleEntity2>)

    @Insert
    abstract fun insertAndReturnList(entityList: List<ExampleEntity2>): List<Long>

    @Insert
    abstract fun insertListAndReturnIdsArray(entityList: List<ExampleEntity2>): Array<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun replace(entityList: List<ExampleEntity2>)

    @Query("SELECT * FROM ExampleEntity2 WHERE uid = :uid")
    abstract fun findByUid(uid: Long): ExampleEntity2?

    @Query("SELECT * FROM ExampleEntity2 WHERE uid = :uid")
    abstract suspend fun findByUidAsync(uid: Long): ExampleEntity2?

    @Query("SELECT * FROM ExampleEntity2 WHERE uid > :uid AND someNumber > :min")
    abstract suspend fun findLarge(uid: Long, min: Long): ExampleEntity2?

    @Query("SELECT * FROM ExampleEntity2 WHERE uid > :uid AND someNumber > :min")
    abstract suspend fun findLargeAsync(uid: Long, min: Long): List<ExampleEntity2>

    @Query("SELECT * FROM ExampleEntity2 WHERE name = :name")
    abstract fun findWithNullableParam(name: String?): List<ExampleEntity2>

    @Query("SELECT name FROM ExampleEntity2 WHERE uid = :uid")
    abstract fun findNameByUid(uid: Long): String?

    @Query("SELECT ExampleEntity2.*, ExampleLinkEntity.* FROM " +
            " ExampleEntity2 LEFT JOIN ExampleLinkEntity ON ExampleEntity2.uid = ExampleLinkEntity.fkValue " +
            "WHERE ExampleEntity2.uid = :uid")
    abstract fun findByUidWithLinkEntity(uid: Long): ExampleEntity2WithExampleLinkEntity?

    @Query("SELECT * FROM ExampleEntity2 ORDER BY uid")
    abstract fun findAll(): List<ExampleEntity2>

    @Query("SELECT * FROM ExampleEntity2")
    abstract suspend fun findAllAsync(): List<ExampleEntity2>

    @Update
    abstract fun updateSingleItem(entity: ExampleEntity2)

    @Update
    abstract fun updateSingleItemAndReturnCount(entity: ExampleEntity2): Int

    @Update
    abstract fun updateList(updateEntityList: List<ExampleEntity2>)

    @Query("SELECT * FROM ExampleEntity2")
    abstract fun findByMinUidLive(): DoorLiveData<List<ExampleEntity2>>

    @Query("UPDATE ExampleEntity2 SET name = :newName WHERE someNumber >= :num")
    abstract fun updateByParam(newName: String, num: Long) : Int

    @Query("UPDATE exampleentity2 SET name = :newName WHERE someNumber >= :number")
    abstract fun updateByParamNoReturn(newName: String, number: Long)

    @Delete
    abstract fun deleteSingle(entity: ExampleEntity2)

    @Delete
    abstract fun deleteList(deleteList: List<ExampleEntity2>)

    @Query("SELECT COUNT(*) FROM ExampleEntity2")
    abstract fun countNumEntities(): Int

    @Query("SELECT COUNT(*) FROM ExampleEntity2")
    abstract suspend fun countNumEntitiesAsync(): Int

    @Query("SELECT * FROM ExampleEntity2 WHERE uid IN (:uidList)")
    abstract fun queryUsingArray(uidList: List<Long>): List<ExampleEntity2>

    @RawQuery
    abstract fun rawQueryForList(query: DoorQuery): List<ExampleEntity2>

    @RawQuery
    abstract fun rawQueryForListWithEmbeddedVals(query: DoorQuery): List<ExampleEntity2WithExampleLinkEntity>

    @RawQuery
    abstract fun rawQueryForSingleValue(query: DoorQuery): ExampleEntity2?

    @Query("SELECT * FROM ExampleEntity2")
    abstract fun queryAllLive(): DataSource.Factory<Int, ExampleEntity2>

    @RawQuery
    abstract fun rawQueryWithArrParam(query: DoorQuery): List<ExampleEntity2>

}