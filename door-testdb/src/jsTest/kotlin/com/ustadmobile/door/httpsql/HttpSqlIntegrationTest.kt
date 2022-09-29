package com.ustadmobile.door.httpsql

import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.DataSourceAsync
import com.ustadmobile.door.util.systemTimeInMillis
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.contentnegotiation.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpSqlIntegrationTest {

    class HttpSqlIntegrationTestContext(
        val dataSource: DataSourceAsync,
        val json: Json,
        val httpClient: HttpClient,
    )

    suspend fun httpDataSourceTest(testBlock: suspend HttpSqlIntegrationTestContext.() -> Unit) {
        val json = Json { encodeDefaults = true }
        val httpClient = HttpClient(Js) {
            install(ContentNegotiation) {
                json()
            }
        }
        val httpDataSource = HttpSqlDataSource("http://localhost:8098/httpsql/", httpClient, json)

        try {
            testBlock(HttpSqlIntegrationTestContext(httpDataSource, json, httpClient))
        }finally {
            httpClient.close()
        }
    }

    @Test
    fun givenEntityInserted_whenQueried_thenShouldReturnResult()  = GlobalScope.promise{
        httpDataSourceTest {
            val connection = dataSource.getConnectionAsync()
            assertNotNull(connection, "Opened connection with server")
            val timeNow = systemTimeInMillis()
            val numChanges = connection.createStatement().executeUpdateAsyncJs(
                "INSERT INTO RepEntity(reLastChangedBy, reLastChangeTime, reNumField, reString, reBoolean) " +
                        "VALUES(0, 0, 42, 'Hello', 1)")
            assertTrue(numChanges > 0, "Ran query with changes implemented")
        }
    }


}