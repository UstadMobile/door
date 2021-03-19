package com.ustadmobile.door.jdbc

expect interface PreparedStatement {

    fun setString(index: Int, value: String)

    fun executeUpdate(): Int

    fun executeQuery(): ResultSet

    fun close()
}