package com.ustadmobile.door.httpsql

import com.ustadmobile.door.ext.requireSuffix
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_CONNECTION_OPEN
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

    private val url = url.requireSuffix("/")

    override fun getConnection(): Connection {
        throw SQLException("HttpSqlDataSource: connection must be opened async")
    }

    override suspend fun getConnectionAsync(): Connection {
        val destUrl = "$url/$PATH_CONNECTION_OPEN"
        println("Attempt to connect to $destUrl")
        val connectionInfo = httpClient.get("$url/$PATH_CONNECTION_OPEN").body<HttpSqlConnectionInfo>()
        return HttpSqlConnection(url, connectionInfo, httpClient, json)
    }
}