package com.ustadmobile.door.jdbc

expect interface ResultSet {

    fun next(): Boolean

    fun getString(columnName: String): String?

    fun getBoolean(columnName: String): Boolean?

    fun getByte(columnName: String): Byte?

    fun getShort(columnName: String): Short?

    fun getInt(columnName: String): Int?

    fun getLong(columnName: String): Long?

    fun getFloat(columnName: String): Float?

    fun getDouble(columnName: String): Double?

    fun getBigDecimal(columnName: String): Any?

    fun getBytes(columnName: String): ByteArray?

    fun getDate(columnName: String): Any?

    fun getTime(columnName: String): Any?

    fun getTimestamp(columnName: String): Any?

    fun getObject(columnName: String): Any?

    fun close()
}