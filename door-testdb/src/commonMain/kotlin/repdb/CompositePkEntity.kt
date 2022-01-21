package repdb

import androidx.room.Entity

@Entity(primaryKeys = arrayOf("code1", "code2"))
class CompositePkEntity {

    var code1: Long = 0

    var code2: Long = 0

    var name: String? = null

}