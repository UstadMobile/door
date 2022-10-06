package com.ustadmobile.door.httpsql

import com.ustadmobile.door.ext.bodyAsJsonObject
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.jdbc.Statement
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

open class HttpSqlStatement(
    private val connection: HttpSqlConnection
) : Statement {

    private var timeOutSecs = 30

    override fun executeUpdate(sql: String): Int {
        throw SQLException("Synchronous update not supported for HttpSql!")
    }

    override suspend fun executeUpdateAsyncJs(sql: String): Int {
        val updateResult = connection.httpClient.post("${connection.endpointUrl}/connection" +
                "/${connection.httpSqlConnectionInfo.connectionId}/statement/update"
        ) {
            setBody(sql)
        }.bodyAsJsonObject(connection.json)

        return updateResult[HttpSqlPaths.KEY_EXEC_UPDATE_NUM_ROWS_CHANGED]?.jsonPrimitive?.int ?: 0
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
        timeOutSecs = seconds
    }
}