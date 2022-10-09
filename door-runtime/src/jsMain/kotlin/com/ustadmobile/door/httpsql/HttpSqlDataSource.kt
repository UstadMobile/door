package com.ustadmobile.door.httpsql

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.DataSourceAsync
import com.ustadmobile.door.jdbc.SQLException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.Json

class HttpSqlDataSource(
    url: String,
    private val httpClient: HttpClient,
    private val json: Json,
) : DataSource, DataSourceAsync {

    internal val url = url.removeSuffix("/")
        .replaceFirst("httpsql://", "http://")
        .replaceFirst("httpsqls://", "https://")

    override fun getConnection(): Connection {
        throw SQLException("HttpSqlDataSource: connection must be opened async")
    }

    override suspend fun getConnectionAsync(): Connection {
        val connectionInfo = httpClient.get("$url/connection/open").body<HttpSqlConnectionInfo>()
        return HttpSqlConnection(url, connectionInfo, httpClient, json)
    }

    companion object {

        const val PROTOCOL_HTTPSQL_PREFIX = "httpsql:"
    }
}