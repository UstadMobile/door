package com.ustadmobile.door.jdbc

actual interface Statement {

    actual fun executeUpdate(sql: String): Int

    suspend fun executeUpdateAsyncJs(sql: String): Int

    actual fun close()

    actual fun isClosed(): Boolean

    actual fun getConnection(): Connection

    actual fun getGeneratedKeys(): ResultSet

    actual fun setQueryTimeout(seconds: Int)

}