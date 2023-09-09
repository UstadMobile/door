package db3

import androidx.room.Insert
import com.ustadmobile.door.annotation.DoorDao

@DoorDao
expect abstract class MemberDao {

    @Insert
    abstract suspend fun insertAsync(member: Member): Long

}