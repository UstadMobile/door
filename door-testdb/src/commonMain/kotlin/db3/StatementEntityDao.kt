package db3

import androidx.room.Insert
import androidx.room.Query
import com.ustadmobile.door.annotation.DoorDao
import com.ustadmobile.door.annotation.HttpAccessible
import com.ustadmobile.door.annotation.Repository
import kotlinx.coroutines.flow.Flow

@DoorDao
@Repository

expect abstract class StatementEntityDao {

    @Insert
    abstract suspend fun insertAsync(entity: StatementEntity)

    @Query("""
        SELECT StatementEntity.*
          FROM StatementEntity
    """)
    abstract suspend fun findAllAsync(): List<StatementEntity>

    @Query("""
        SELECT StatementEntity.*
          FROM StatementEntity
         WHERE StatementEntity.uidHi = :uidHi
           AND StatementEntity.uidLo = :uidLo
    """)
    abstract fun findByUidAsFlow(uidHi: Long, uidLo: Long): Flow<StatementEntity?>

    @HttpAccessible(clientStrategy = HttpAccessible.ClientStrategy.PULL_REPLICATE_ENTITIES)
    @Query("""
        SELECT StatementEntity.*
          FROM StatementEntity
         WHERE StatementEntity.uidHi = :uidHi
           AND StatementEntity.uidLo = :uidLo
    """)
    abstract suspend fun findByUidAsync(uidHi: Long, uidLo: Long): StatementEntity?

}