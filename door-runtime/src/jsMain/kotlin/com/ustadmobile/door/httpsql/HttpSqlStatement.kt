package com.ustadmobile.door.httpsql

import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.jdbc.Statement
import io.ktor.client.call.*
import io.ktor.client.request.*

open class HttpSqlStatement(
    private val connection: HttpSqlConnection
) : Statement {

    override fun executeUpdate(sql: String): Int {
        throw SQLException("Synchronous update not supported for HttpSql!")
    }

    override suspend fun executeUpdateAsyncJs(sql: String): Int {
        val updateResult: HttpSqlUpdateResult = connection.httpClient.post("${connection.endpointUrl}/connection" +
                "/${connection.httpSqlConnectionInfo.connectionId}/statement/update"
        ) {
            setBody(sql)
        }.body()

        return updateResult.updates
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