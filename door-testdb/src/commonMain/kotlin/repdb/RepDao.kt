package repdb

import com.ustadmobile.door.annotation.DoorDao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ustadmobile.door.annotation.*
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Suppress("unused") //Some functions are not used directly, but we still want to make sure they compile
@DoorDao
@Repository
expect interface RepDao: RepDaoInterface<RepEntity> {

    @Insert
    suspend fun insertDoorNodeAsync(node: DoorNode)

    @Insert
    fun insertDoorNode(node: DoorNode)


    @Insert
    suspend fun insertAsync(repEntity: RepEntity): Long

    @Insert
    fun insert(repEntity: RepEntity): Long

    @Insert
    fun insertList(repEntityList: List<RepEntity>)

    @Update
    fun update(repEntity: RepEntity)


    @Update
    suspend fun updateAsync(repEntity: RepEntity)

    @RepoHttpAccessible
    @Query("""
    SELECT COUNT(*)
      FROM RepEntity
    """)
    fun countEntities(): Int


    @Query("""
    SELECT RepEntity.*
      FROM RepEntity
     WHERE RepEntity.rePrimaryKey = :uid 
    """)
    fun findByUid(uid: Long): RepEntity?


    @Query("""
    SELECT RepEntity.*
      FROM RepEntity
     WHERE RepEntity.rePrimaryKey = :uid 
    """)
    suspend fun findByUidAsync(uid: Long): RepEntity?

    @Query("""
    SELECT RepEntity.*
      FROM RepEntity
    """)
    suspend fun findAllAsync(): List<RepEntity>


    @RepoHttpAccessible
    @Repository(Repository.METHOD_DELEGATE_TO_WEB)
    @Insert
    suspend fun insertHttp(entity: RepEntity) : Long

    @Query("""
        SELECT COALESCE(
                    (SELECT nodeClientId 
                       FROM SyncNode 
                      LIMIT 1), 0)
    """)
    suspend fun selectSyncNodeId(): Long


    @Query("""
        SELECT RepEntity.*
          FROM RepEntity
         WHERE reString IN (:strList) 
    """)
    suspend fun findInStringList(strList: List<String>): List<RepEntity>


    @Query("""SELECT MAX(:num1, :num2)""")
    @SqliteOnly
    suspend fun sqliteOnlyFun(num1: Int, num2: Int): Long

    @Query("""
        SELECT * FROM RepEntity
    """)
    fun findAllAsFlow(): Flow<List<RepEntity>>


    @Query("""
        SELECT * FROM RepEntity
    """)
    fun findAllPaged(): PagingSource<Int, RepEntity>


}