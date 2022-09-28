package com.ustadmobile.door.httpsql

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.jdbc.Statement

class HttpSqlStatement(
    private val connection: HttpSqlConnection
) : Statement {

    override fun executeUpdate(sql: String): Int {
        throw SQLException("Synchronous update not supported for HttpSql!")
    }

    override suspend fun executeUpdateAsyncJs(sql: String): Int {
        TODO("Not yet implemented")
    }

    override fun close() {

    }

    override fun isClosed(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getConnection() = connection

    override fun getGeneratedKeys(): ResultSet {
        TODO("Not yet implemented")
    }

    override fun setQueryTimeout(seconds: Int) {
        TODO("Not yet implemented")
    }
}