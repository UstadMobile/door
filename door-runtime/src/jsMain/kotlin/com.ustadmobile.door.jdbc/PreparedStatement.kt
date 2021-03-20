package com.ustadmobile.door.jdbc

actual interface PreparedStatement {


    actual fun setString(index: Int, value: String)

    actual fun setInt(index: Int, value: Int)

    actual fun setLong(index: Int, value: Long)

    actual fun executeUpdate(): Int

    suspend fun executeUpdateAsync(): Int

    actual fun executeQuery(): ResultSet

    suspend fun executeQueryAsync(): ResultSet

    actual fun close()
}