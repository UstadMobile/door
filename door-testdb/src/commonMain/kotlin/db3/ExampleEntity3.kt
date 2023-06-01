package db3

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.LastChangedTime
import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.door.annotation.ReplicationVersionId

@Entity
@ReplicateEntity(tableId = 542)
data class ExampleEntity3(
    @PrimaryKey(autoGenerate = true)
    var eeUid: Long = 0,

    var cardNumber: Int = 0,

    var name: String? = null,

    @LastChangedTime
    @ReplicationVersionId
    var lastUpdatedTime: Long = 0

) {

    companion object {

        const val TABLE_ID = 542
    }
}