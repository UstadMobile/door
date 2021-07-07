package com.ustadmobile.door.jdbc

actual interface Connection {

    actual fun setAutoCommit(commit: Boolean)

    actual fun prepareStatement(param: String?): PreparedStatement

    actual fun commit()

    actual fun close()
}