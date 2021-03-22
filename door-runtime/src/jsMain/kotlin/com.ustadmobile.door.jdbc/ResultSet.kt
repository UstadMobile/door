package com.ustadmobile.door.jdbc

actual interface ResultSet {

    actual fun next(): Boolean

    actual fun getString(columnName: String): String?

    actual fun getBoolean(columnName: String): Boolean?

    actual fun getByte(columnName: String): Byte?

    actual fun getShort(columnName: String): Short?

    actual fun getInt(columnName: String): Int?

    actual fun getFloat(columnName: String): Float?

    actual fun getLong(columnName: String): Long?

    actual fun getDouble(columnName: String): Double?

    actual fun getBigDecimal(columnName: String): Any?

    actual fun getBytes(columnName: String): ByteArray ?

    actual fun getDate(columnName: String): Any?

    actual fun getTime(columnName: String): Any?

    actual fun getTimestamp(columnName: String): Any?

    actual fun getObject(columnName: String): Any?

    actual fun close()
}