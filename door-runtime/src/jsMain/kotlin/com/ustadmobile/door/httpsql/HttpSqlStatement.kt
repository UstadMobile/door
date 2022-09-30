package com.ustadmobile.door.httpsql

import com.ustadmobile.door.httpsql.HttpSqlPaths.KEY_UPDATES
import com.ustadmobile.door.httpsql.HttpSqlPaths.PARAM_CONNECTION_ID
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_STATEMENT_UPDATE
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.jdbc.Statement
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

class HttpSqlStatement(
    private val connection: HttpSqlConnection
) : Statement {

    override fun executeUpdate(sql: String): Int {
        throw SQLException("Synchronous update not supported for HttpSql!")
    }

    override suspend fun executeUpdateAsyncJs(sql: String): Int {
        val bodyText = connection.httpClient.post("${connection.endpointUrl}/connection" +
                "/${connection.httpSqlConnectionInfo.connectionId}/statement/update"
        ) {
            setBody(sql)
        }.bodyAsText()

        val bodyJson = connection.json.decodeFromString(JsonObject.serializer(), bodyText)
        return bodyJson[KEY_UPDATES]?.jsonPrimitive?.int ?: throw SQLException("Could not determine num rows updated")
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