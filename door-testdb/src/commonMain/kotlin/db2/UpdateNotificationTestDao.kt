package db2

import androidx.room.Dao
import androidx.room.Query
import com.ustadmobile.door.entities.UpdateNotification

@Dao
abstract class UpdateNotificationTestDao {

    @Query("SELECT * FROM UpdateNotification WHERE pnDeviceId = :deviceId")
    abstract fun getUpdateNotificationsForDevice(deviceId: Int): List<UpdateNotification>

}