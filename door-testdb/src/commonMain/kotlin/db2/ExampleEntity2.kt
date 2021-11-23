package db2

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity
@Serializable
open class ExampleEntity2(
        @PrimaryKey(autoGenerate = true)
        var uid: Long = 0L,
        var name: String? = "",
        @ColumnInfo(index = true)
        var someNumber: Long = 0L,
        var checked: Boolean = false,) {


        override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false

                other as ExampleEntity2

                if (uid != other.uid) return false
                if (name != other.name) return false
                if (checked != other.checked) return false
                if (someNumber != other.someNumber) return false

                return true
        }

        override fun hashCode(): Int {
                var result = uid.hashCode()
                result = 31 * result + (name?.hashCode() ?: 0)
                result = 31 * result + checked.hashCode()
                result = 31 * result + someNumber.hashCode()
                return result
        }
}