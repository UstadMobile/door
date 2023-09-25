package db3

import androidx.room.Insert
import androidx.room.Query
import com.ustadmobile.door.annotation.DoorDao
import com.ustadmobile.door.annotation.HttpAccessible
import com.ustadmobile.door.annotation.Repository

@DoorDao
@Repository
expect abstract class BadgeDao {

    @Insert
    abstract suspend fun insertAsync(badge: Badge): Long

    @HttpAccessible
    @Query("""
        SELECT Badge.*,0 AS total
          FROM Badge
         WHERE Badge.badgeUid = :uid 
    """)
    abstract suspend fun findBadgeByUid(uid: Long): BadgeWithTotal?

}