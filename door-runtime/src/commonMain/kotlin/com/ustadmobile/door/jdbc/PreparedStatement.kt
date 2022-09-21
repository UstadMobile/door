package com.ustadmobile.door.jdbc

import com.ustadmobile.door.jdbc.types.*

expect interface PreparedStatement : Statement {

    fun setBoolean(index: Int, value: Boolean)

    fun setByte(index: Int, value: Byte)

    fun setShort(index: Int, value: Short)

    fun setInt(index: Int, value: Int)

    fun setLong(index: Int, value: Long)

    fun setFloat(index: Int, value: Float)

    fun setDouble(index: Int, value: Double)

    fun setBigDecimal(index: Int, value: BigDecimal)

    fun setString(index: Int, value: String?)

    fun setBytes(index: Int, value: ByteArray)

    fun setDate(index: Int, value: Date)

    fun setTime(index: Int, value: Time)

    fun setObject(index: Int, value: Any?)

    fun setArray(index: Int, array: com.ustadmobile.door.jdbc.Array)

    fun setNull(parameterIndex: Int, sqlType: Int)

    fun executeUpdate(): Int

    fun executeQuery(): ResultSet

}