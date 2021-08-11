package repdb

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.ReplicationEntityForeignKey

@Entity
class RepEntityTracker {

    @PrimaryKey(autoGenerate = true)
    var trkrPrimaryKey: Long = 0

    @ReplicationEntityForeignKey
    var trkrForeignKey: Long = 0

    var trkrVersionId: Long = 0

    var trkrDestination: Long = 0

    var trkrProcessed: Boolean = false

}