package com.ustadmobile.door.httpsql

import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.room.RoomDatabase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.junit.Test
import org.mockito.kotlin.mock
import io.ktor.server.routing.routing
import io.ktor.serialization.gson.gson
import io.ktor.http.ContentType
import io.ktor.serialization.gson.GsonConverter
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.mockito.kotlin.verify
import io.ktor.client.HttpClient
import io.ktor.server.application.install
import kotlinx.serialization.json.Json


class HttpSqlRouteTest {


    class HttpSqlTestContext(
        val openedConnections: MutableList<Connection>,
        val client: HttpClient,
        val mockDataSource: DataSource,
    )

    private fun testHttpSqlApplication(
        testBlock: TestApplicationBuilder.(HttpSqlTestContext) -> Unit
    ) {
        val openedConnections =  mutableListOf<Connection>()

        val mockDatasource = mock<DataSource>() {
            on { connection }.thenAnswer {
                mock<Connection> { }.also {
                    openedConnections.add(it)
                }
            }
        }
        val mockDb = mock<RoomDatabase>(extraInterfaces = arrayOf(DoorDatabaseJdbc::class)) {
            on { (this as DoorDatabaseJdbc).dataSource }.thenReturn(mockDatasource)
        }

        val json = Json { encodeDefaults = true }

        testApplication {
            environment {
                config = MapApplicationConfig("ktor.environment" to "test")
            }

            application {
                routing {
                    HttpSql(mockDb, { true }, json)
                }
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    gson {
                        register(ContentType.Application.Json, GsonConverter())
                        register(ContentType.Any, GsonConverter())
                    }
                }
            }

            val client = createClient {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    gson()
                }
            }

            testBlock(HttpSqlTestContext(openedConnections, client, mockDatasource))

        }
    }

    @Test
    fun givenOpenDatabase_whenQueried_shouldReturnResult() = testHttpSqlApplication { testContext ->
        val client = testContext.client
        val connectionInfo: HttpSqlConnectionInfo = runBlocking {
            client.get("/open").body()
        }

        runBlocking {
            client.get("/close?connectionId=${connectionInfo.connectionId}")
        }

        Assert.assertTrue(connectionInfo.connectionId != 0)
        verify(testContext.mockDataSource).connection
        verify(testContext.openedConnections.first()).close()
    }


}