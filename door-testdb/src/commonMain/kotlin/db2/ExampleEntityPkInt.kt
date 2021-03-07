package db2

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ExampleEntityPkInt(
        @PrimaryKey(autoGenerate = true)  var pk: Int = 0,
        var str: String? = null)