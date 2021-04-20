package dbonly

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class VanillaEntity {

    @PrimaryKey(autoGenerate = true)
    var vanillaUid: Long = 0

    var vanillaNum: Int = 0

    var vanillaStr: String? = null

}