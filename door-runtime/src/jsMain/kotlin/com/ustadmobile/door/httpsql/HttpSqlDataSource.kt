package com.ustadmobile.door.httpsql

import com.ustadmobile.door.ext.requireSuffix
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.DataSourceAsync
import com.ustadmobile.door.jdbc.SQLException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class HttpSqlDataSource(
    url: String,
    val httpClient: HttpClient,
) : DataSource, DataSourceAsync {

    private val url = url.requireSuffix("/")

    override fun getConnection(): Connection {
        throw SQLException("HttpSqlDataSource: connection must be opened async")
    }

    override suspend fun getConnectionAsync(): Connection {
        val connectionInfo = httpClient.get("$url/open").body<HttpSqlConnectionInfo>()
        return HttpSqlConnection(url, connectionInfo)
    }
}