package com.ustadmobile.door.jdbc

import com.ustadmobile.door.jdbc.types.BigDecimal
import com.ustadmobile.door.jdbc.types.Date
import com.ustadmobile.door.jdbc.types.Time
import com.ustadmobile.door.jdbc.types.TimeStamp

expect interface ResultSet {

    fun next(): Boolean

    fun getString(columnName: String): String?

    fun getString(columnIndex: Int): String?

    fun getBoolean(columnName: String): Boolean

    fun getBoolean(columnIndex: Int): Boolean

    fun getByte(columnName: String): Byte

    fun getByte(columnIndex: Int): Byte

    fun getShort(columnName: String): Short

    fun getShort(columnIndex: Int): Short

    fun getInt(columnName: String): Int

    fun getInt(columnIndex: Int): Int

    fun getLong(columnName: String): Long

    fun getLong(columnIndex: Int): Long

    fun getFloat(columnName: String): Float

    fun getFloat(columnIndex: Int): Float

    fun getDouble(columnName: String): Double

    fun getDouble(columnIndex: Int): Double

    fun getBigDecimal(columnName: String): BigDecimal?

    fun getBytes(columnName: String): ByteArray?

    fun getDate(columnName: String): Date?

    fun getTime(columnName: String): Time?

    fun getTimestamp(columnName: String): TimeStamp?

    fun getObject(columnName: String): Any?

    fun getObject(columnIndex: Int): Any?

    fun wasNull(): Boolean

    fun getMetaData(): ResultSetMetaData

    fun close()

    fun isClosed(): Boolean
}