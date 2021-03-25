package com.ustadmobile.door.jdbc

import com.ustadmobile.door.jdbc.types.BigDecimal
import com.ustadmobile.door.jdbc.types.Date
import com.ustadmobile.door.jdbc.types.Time
import com.ustadmobile.door.jdbc.types.TimeStamp

expect interface ResultSet {

    fun next(): Boolean

    fun getString(columnName: String): String?

    fun getBoolean(columnName: String): Boolean

    fun getByte(columnName: String): Byte

    fun getShort(columnName: String): Short

    fun getInt(columnName: String): Int

    fun getLong(columnName: String): Long

    fun getFloat(columnName: String): Float

    fun getDouble(columnName: String): Double

    fun getBigDecimal(columnName: String): BigDecimal?

    fun getBytes(columnName: String): ByteArray?

    fun getDate(columnName: String): Date?

    fun getTime(columnName: String): Time?

    fun getTimestamp(columnName: String): TimeStamp?

    fun getObject(columnName: String): Any?

    fun close()
}