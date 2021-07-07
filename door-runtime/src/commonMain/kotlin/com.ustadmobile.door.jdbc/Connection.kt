package com.ustadmobile.door.jdbc

expect interface Connection {

    fun setAutoCommit(commit: Boolean)

    fun prepareStatement(param: String?): PreparedStatement

    fun commit()

    fun close()
}