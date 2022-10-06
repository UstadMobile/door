package com.ustadmobile.door.httpsql

import com.ustadmobile.door.jdbc.AsyncConnection
import com.ustadmobile.door.jdbc.DataSourceAsync
import com.ustadmobile.door.jdbc.ext.executeUpdateAsync
import com.ustadmobile.door.jdbc.ext.useStatementAsync
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
            val asyncConnection = httpDataSource.getConnectionAsync() as AsyncConnection
            asyncConnection.createStatement().useStatementAsync {
                it.executeUpdateAsync("DELETE FROM RepEntity")
            }

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
            val numChanges = connection.createStatement().executeUpdateAsyncJs(
                "INSERT INTO RepEntity(reLastChangedBy, reLastChangeTime, reNumField, reString, reBoolean) " +
                        "VALUES(0, 0, 42, 'Hello', 1)")
            assertTrue(numChanges > 0, "Ran query with changes implemented")
        }
    }

    @Test
    fun givenPreparedStatementCreated_whenExecuteUpdated_thenChangeShouldTakeEffect() = GlobalScope.promise {
        httpDataSourceTest {
            val connection = dataSource.getConnectionAsync() as AsyncConnection
            val preparedStatement = connection.prepareStatementAsync(
                "INSERT INTO RepEntity(reLastChangedBy, reLastChangeTime, reNumField, reString, reBoolean) " +
                        "VALUES(?, ?, ?, ?, ?)")
            preparedStatement.setLong(1, 0)
            preparedStatement.setLong(2, 0)
            preparedStatement.setInt(3, 50)
            preparedStatement.setString(4, "Hello")
            preparedStatement.setBoolean(5, false)
            val numUpdates = preparedStatement.executeUpdateAsync()
            assertTrue(numUpdates > 0, "Ran query with changes implemented")
        }
    }

    @Test
    fun givenPreparedStatementCreated_whenQueryExecuted_thenShouldReturnResult() = GlobalScope.promise {
        httpDataSourceTest {
            val connection = dataSource.getConnectionAsync() as AsyncConnection
            connection.createStatement().useStatementAsync {
                it.executeUpdateAsync("INSERT INTO RepEntity(reLastChangedBy, reLastChangeTime, reNumField, reString, reBoolean) " +
                        "VALUES (0, 0, 100, 'test', 0)")
            }

            val preparedStatement = connection.prepareStatementAsync(
                "SELECT * FROM RepEntity WHERE reNumField > ?"
            )
            preparedStatement.setInt(1, 50)
            val results = preparedStatement.executeQueryAsyncInt()
            assertTrue(results.next())
            assertEquals(100, results.getInt("reNumField"))
        }
    }


}