package com.ustadmobile.door.jdbc

import com.ustadmobile.door.jdbc.types.BigDecimal
import com.ustadmobile.door.jdbc.types.Date
import com.ustadmobile.door.jdbc.types.Time
import com.ustadmobile.door.jdbc.types.TimeStamp

actual interface ResultSet {

    actual fun next(): Boolean

    actual fun getString(columnName: String): String?

    actual fun getString(columnIndex: Int): String?

    actual fun getBoolean(columnName: String): Boolean

    actual fun getBoolean(columnIndex: Int): Boolean

    actual fun getByte(columnName: String): Byte

    actual fun getByte(columnIndex: Int): Byte

    actual fun getShort(columnName: String): Short

    actual fun getShort(columnIndex: Int): Short

    actual fun getInt(columnName: String): Int

    actual fun getInt(columnIndex: Int): Int

    actual fun getLong(columnName: String): Long

    actual fun getLong(columnIndex: Int): Long

    actual fun getFloat(columnName: String): Float

    actual fun getFloat(columnIndex: Int): Float

    actual fun getDouble(columnName: String): Double

    actual fun getDouble(columnIndex: Int): Double

    actual fun getBigDecimal(columnName: String): BigDecimal?

    actual fun getBytes(columnName: String): ByteArray?

    actual fun getDate(columnName: String): Date?

    actual fun getTime(columnName: String): Time?

    actual fun getTimestamp(columnName: String): TimeStamp?

    actual fun getObject(columnName: String): Any?

    actual fun getObject(columnIndex: Int): Any?

    actual fun wasNull(): Boolean

    actual fun getMetaData(): ResultSetMetaData

    actual fun close()

    actual fun isClosed(): Boolean

}