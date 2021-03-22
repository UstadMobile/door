package com.ustadmobile.door.jdbc

actual interface PreparedStatement {

    suspend fun executeUpdateAsync(): Int

    suspend fun executeQueryAsyncInt(): ResultSet

    actual fun setBoolean(index: Int, value: Boolean)

    actual fun setByte(index: Int, value: Byte)

    actual fun setShort(index: Int, value: Short)

    actual fun setInt(index: Int, value: Int)

    actual fun setLong(index: Int, value: Long)

    actual fun setFloat(index: Int, value: Float)

    actual fun setDouble(index: Int, value: Double)

    actual fun setBigDecimal(index: Int, value: Any)

    actual fun setString(index: Int, value: String)

    actual fun setBytes(index: Int, value: ByteArray)

    actual fun setDate(index: Int, value: Any)

    actual fun setTime(index: Int, value: Any)

    actual fun setTimestamp(index: Int, value: Any)

    actual fun setObject(index: Int, value: Any)

    actual fun executeUpdate(): Int

    actual fun executeQuery(): ResultSet

    actual fun close()
}