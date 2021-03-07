package db2

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ExampleLinkEntity(@PrimaryKey var eeUid: Long = 0L, var fkValue: Long = 0L)
