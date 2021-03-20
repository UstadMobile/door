package com.ustadmobile.door.jdbc

expect interface PreparedStatement {

    fun setString(index: Int, value: String)

    fun setInt(index: Int, value: Int)

    fun setLong(index: Int, value: Long)

    fun executeUpdate(): Int

    fun executeQuery(): ResultSet

    fun close()
}