package com.ustadmobile.door.jdbc

actual interface PreparedStatement {


    actual fun setString(index: Int, value: String)

    actual fun executeUpdate(): Int

    actual fun executeQuery(): ResultSet

    actual fun close()
}