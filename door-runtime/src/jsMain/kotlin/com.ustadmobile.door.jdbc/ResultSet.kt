package com.ustadmobile.door.jdbc

import com.ustadmobile.door.jdbc.types.BigDecimal
import com.ustadmobile.door.jdbc.types.Date
import com.ustadmobile.door.jdbc.types.Time
import com.ustadmobile.door.jdbc.types.TimeStamp

actual interface ResultSet {

    actual fun next(): Boolean

    actual fun getString(columnName: String): String?

    actual fun getBoolean(columnName: String): Boolean

    actual fun getByte(columnName: String): Byte

    actual fun getShort(columnName: String): Short

    actual fun getInt(columnName: String): Int

    actual fun getLong(columnName: String): Long

    actual fun getFloat(columnName: String): Float

    actual fun getDouble(columnName: String): Double

    actual fun getBigDecimal(columnName: String): BigDecimal?

    actual fun getBytes(columnName: String): ByteArray?

    actual fun getDate(columnName: String): Date?

    actual fun getTime(columnName: String): Time?

    actual fun getTimestamp(columnName: String): TimeStamp?

    actual fun getObject(columnName: String): Any?

    actual fun close()

}