package com.ustadmobile.door.jdbc

expect interface PreparedStatement {

    fun setBoolean(index: Int, value: Boolean)

    fun setByte(index: Int, value: Byte)

    fun setShort(index: Int, value: Short)

    fun setInt(index: Int, value: Int)

    fun setLong(index: Int, value: Long)

    fun setFloat(index: Int, value: Float)

    fun setDouble(index: Int, value: Double)

    fun setBigDecimal(index: Int, value: Any)

    fun setString(index: Int, value: String)

    fun setBytes(index: Int, value: ByteArray)

    fun setDate(index: Int, value: Any)

    fun setTime(index: Int, value: Any)

    fun setTimestamp(index: Int, value: Any)

    fun setObject(index: Int, value: Any)

    fun executeUpdate(): Int

    fun executeQuery(): ResultSet

    fun close()
}