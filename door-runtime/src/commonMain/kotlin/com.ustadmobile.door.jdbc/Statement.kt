package com.ustadmobile.door.jdbc

expect interface Statement {

    fun isClosed(): Boolean

    fun getConnection(): Connection

    fun close()

    fun getGeneratedKeys(): ResultSet

    fun setQueryTimeout(seconds: Int)

    fun executeUpdate(sql: String): Int

}
