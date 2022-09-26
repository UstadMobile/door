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
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.serialization.gson.gson
import io.ktor.http.ContentType
import io.ktor.serialization.gson.GsonConverter
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.mockito.kotlin.verify


class HttpSqlRouteTest {

    @Test
    fun givenOpenDatabase_whenQueried_shouldReturnResult() {

        val openedConnections = mutableListOf<Connection>()

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


        testApplication {
            environment {
                config = MapApplicationConfig("ktor.environment" to "test")
            }

            application {
                routing {
                    HttpSql(mockDb, { true })
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

            val connectionInfo: HttpSqlConnectionInfo = runBlocking {
                client.get("/open").body()
            }

            runBlocking {
                client.get("/close?connectionId=${connectionInfo.connectionId}")
            }

            Assert.assertTrue(connectionInfo.connectionId != 0)
            verify(mockDatasource).connection
            verify(openedConnections.first()).close()

        }
    }

}