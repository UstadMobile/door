package com.ustadmobile.door.jdbc

import com.ustadmobile.door.jdbc.types.BigDecimal
import com.ustadmobile.door.jdbc.types.Date
import com.ustadmobile.door.jdbc.types.Time
import com.ustadmobile.door.jdbc.types.TimeStamp

actual interface PreparedStatement {

    actual fun setBoolean(index: Int, value: Boolean)

    actual fun setByte(index: Int, value: Byte)

    actual fun setShort(index: Int, value: Short)

    actual fun setInt(index: Int, value: Int)

    actual fun setLong(index: Int, value: Long)

    actual fun setFloat(index: Int, value: Float)

    actual fun setDouble(index: Int, value: Double)

    actual fun setBigDecimal(index: Int, value: BigDecimal)

    actual fun setString(index: Int, value: String)

    actual fun setBytes(index: Int, value: ByteArray)

    actual fun setDate(index: Int, value: Date)

    actual fun setTime(index: Int, value: Time)

    actual fun setTimestamp(index: Int, value: TimeStamp)

    actual fun setObject(index: Int, value: Any)

    actual fun executeUpdate(): Int

    suspend fun executeUpdateAsync(): Int

    suspend fun executeQueryAsyncInt(): ResultSet

    actual fun executeQuery(): ResultSet

    actual fun close()

}