package db2

import androidx.room.*
import app.cash.paging.PagingSource
import com.ustadmobile.door.DoorQuery
import com.ustadmobile.door.annotation.DoorDao
import kotlinx.coroutines.flow.Flow

@Suppress("unused") //Even if functions here are unused, they are still testing that the generated code compiles
@DoorDao
expect abstract class ExampleDao2 {

    @Insert
    abstract fun insertAndReturnId(entity: ExampleEntity2): Long

    @Insert
    abstract suspend fun insertAsync(entity: ExampleEntity2)

    @Insert
    abstract suspend fun insertAsyncAndGiveId(entity: ExampleEntity2) : Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertOrIgnore(entity: ExampleEntity2)

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

    @Query("SELECT name FROM ExampleEntity2 WHERE uid = :uid")
    abstract suspend fun findNameByUidAsync(uid: Long): String?

    @Query("SELECT ExampleEntity2.*, ExampleLinkEntity.* FROM " +
            " ExampleEntity2 LEFT JOIN ExampleLinkEntity ON ExampleEntity2.uid = ExampleLinkEntity.fkValue " +
            "WHERE ExampleEntity2.uid = :uid")
    abstract suspend fun findByUidWithLinkEntityAsync(uid: Long): ExampleEntity2WithExampleLinkEntity?

    @Query("SELECT * FROM ExampleEntity2 ORDER BY uid")
    abstract fun findAll(): List<ExampleEntity2>

    @Query("SELECT * FROM ExampleEntity2")
    abstract suspend fun findAllAsync(): List<ExampleEntity2>

    @Query("SELECT name FROM ExampleEntity2")
    abstract suspend fun findAllStrings(): List<String?>

    @Query("SELECT name FROM ExampleEntity2")
    abstract fun findAllStringsSync(): List<String?>

    @Update
    abstract suspend fun updateSingleItemAsync(entity: ExampleEntity2)

    @Update
    abstract suspend fun updateSingleItemAndReturnCountAsync(entity: ExampleEntity2): Int

    @Update
    abstract suspend fun updateListAsync(updateEntityList: List<ExampleEntity2>)

    @Query("UPDATE ExampleEntity2 SET name = :newName WHERE someNumber >= :num")
    abstract fun updateByParam(newName: String, num: Long) : Int

    @Query("UPDATE ExampleEntity2 SET name = :newName WHERE someNumber >= :num")
    abstract suspend fun updateByParamAsync(newName: String, num: Long) : Int

    @Query("UPDATE exampleentity2 SET name = :newName WHERE someNumber >= :number")
    abstract fun updateByParamNoReturn(newName: String, number: Long)

    @Delete
    abstract fun deleteSingle(entity: ExampleEntity2)

    @Delete
    abstract fun deleteList(deleteList: List<ExampleEntity2>)

    @Delete
    abstract suspend fun deleteListAsync(deleteList: List<ExampleEntity2>): Int

    @Query("SELECT COUNT(*) FROM ExampleEntity2")
    abstract fun countNumEntities(): Int

    @Query("SELECT COUNT(*) FROM ExampleEntity2")
    abstract suspend fun countNumEntitiesAsync(): Int

    @Query("SELECT * FROM ExampleEntity2 WHERE uid IN (:uidList)")
    abstract fun queryUsingArray(uidList: List<Long>): List<ExampleEntity2>

    @RawQuery
    abstract fun rawQueryForList(query: DoorQuery): List<ExampleEntity2>

    @RawQuery
    abstract suspend fun rawQueryForListAsyc(query: DoorQuery): List<ExampleEntity2>

    @RawQuery
    abstract fun rawQueryForListWithEmbeddedVals(query: DoorQuery): List<ExampleEntity2WithExampleLinkEntity>

    @RawQuery
    abstract fun rawQueryForSingleValue(query: DoorQuery): ExampleEntity2?

    @RawQuery
    abstract fun rawQueryWithArrParam(query: DoorQuery): List<ExampleEntity2>

    @Query("""
   INSERT INTO ExampleEntity2(uid, name, someNumber)
        SELECT (uid + 1) AS uid, name AS name, (someNumber * 2) AS someNumber
          FROM ExampleEntity2
         WHERE uid = :uid 
    """)
    abstract fun insertFromSelectQuery(uid: Int)

    @Query("SELECT * FROM ExampleEntity2")
    abstract fun queryAllAsFlow(): Flow<List<ExampleEntity2>>

    @Query("SELECT * FROM ExampleEntity2 WHERE someNumber > :greaterThan LIMIT 1")
    abstract fun findSingleNotNullableEntity(greaterThan: Int): ExampleEntity2

    @Query("SELECT * FROM ExampleEntity2 WHERE someNumber > :greaterThan LIMIT 1")
    abstract suspend fun findSingleNotNullableEntityAsync(greaterThan: Int): ExampleEntity2

    @Query("SELECT someNumber FROM ExampleEntity2 WHERE someNumber > :greaterThan LIMIT 1")
    abstract fun findSingleNotNullablePrimitive(greaterThan: Int): Int

    @Query("SELECT someNumber FROM ExampleEntity2 WHERE someNumber > :greaterThan LIMIT 1")
    abstract suspend fun findSingleNotNullablePrimitiveAsync(greaterThan: Int): Int

    @Query("SELECT someNumber FROM ExampleEntity2 WHERE someNumber > :greaterThan LIMIT 1")
    abstract fun findSingleNullablePrimitive(greaterThan: Int): Int?

    @Query("SELECT someNumber FROM ExampleEntity2 WHERE someNumber > :greaterThan LIMIT 1")
    abstract suspend fun findSingleNullablePrimitiveAsync(greaterThan: Long): Long?

    @Query("""
        SELECT ExampleEntity2.* FROM ExampleEntity2
         WHERE (CASE
                WHEN :rewardsNum IS NULL THEN (ExampleEntity2.rewardsCardNumber IS NULL)
                ELSE (ExampleEntity2.rewardsCardNumber = :rewardsNum) 
                END)
    """)
    abstract suspend fun findWithNullableIntAsync(rewardsNum: Int?): ExampleEntity2?

    @Query("""
        SELECT * 
          FROM ExampleEntity2
         WHERE rewardsCardNumber IS NOT NULL
           AND rewardsCardNumber >= :minNumber 
      ORDER BY rewardsCardNumber    
    """)
    abstract fun findAllWithRewardNumberAsPagingSource(minNumber: Int): PagingSource<Int, ExampleEntity2>

    @Query("""
        SELECT * 
          FROM ExampleEntity2
         WHERE rewardsCardNumber IS NOT NULL
           AND rewardsCardNumber >= :minNumber 
      ORDER BY rewardsCardNumber    
    """)
    abstract suspend fun findAllWithRewardNumberAsListAsync(minNumber: Int): List<ExampleEntity2>

    @Query("""
        SELECT * 
          FROM ExampleEntity2
         WHERE someNumber >= :minNumber 
    """)
    abstract suspend fun findByMinSomeNumber(minNumber: Long): List<ExampleEntity2>

}