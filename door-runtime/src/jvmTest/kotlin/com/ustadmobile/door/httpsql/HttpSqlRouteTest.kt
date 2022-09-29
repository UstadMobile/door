package com.ustadmobile.door.httpsql

import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.httpsql.HttpSqlPaths.PARAM_CONNECTION_ID
import com.ustadmobile.door.httpsql.HttpSqlPaths.PARAM_PREPAREDSTATEMENT_ID
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_CONNECTION_CLOSE
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_CONNECTION_OPEN
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_PREPARED_STATEMENT_QUERY
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_PREPARED_STATEMENT_UPDATE
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_PREPARE_STATEMENT
import com.ustadmobile.door.jdbc.*
import com.ustadmobile.door.room.RoomDatabase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.junit.Test
import org.mockito.kotlin.mock
import io.ktor.server.routing.routing
import io.ktor.serialization.gson.gson
import io.ktor.serialization.gson.GsonConverter
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.mockito.kotlin.verify
import io.ktor.client.HttpClient
import io.ktor.http.*
import io.ktor.server.application.install
import kotlinx.serialization.json.Json
import org.mockito.kotlin.any


class HttpSqlRouteTest {


    class HttpSqlTestContext(
        val openedConnections: MutableList<Connection>,
        val client: HttpClient,
        val mockDataSource: DataSource,
        val preparedStatements: MutableList<PreparedStatement>
    )

    private fun testHttpSqlApplication(
        testBlock: TestApplicationBuilder.(HttpSqlTestContext) -> Unit
    ) {
        val openedConnections =  mutableListOf<Connection>()

        val preparedStatements = mutableListOf<PreparedStatement>()

        val mockDatasource = mock<DataSource>() {
            on { connection }.thenAnswer {
                mock<Connection> {
                    on {
                        prepareStatement(any(), any() as Int)
                    }.thenAnswer {
                        mock<PreparedStatement> {
                            on { executeQuery() }.thenAnswer {
                                mock<ResultSet> {
                                    on { metaData }.thenAnswer {
                                        mock<ResultSetMetaData> {
                                            on { columnCount }.thenAnswer { 0 }
                                        }
                                    }
                                }
                            }

                        }.also {
                            preparedStatements += it
                        }
                    }
                }.also {
                    openedConnections += it
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
                    HttpSql({ mockDb }, { true }, json)
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

            testBlock(HttpSqlTestContext(openedConnections, client, mockDatasource, preparedStatements))

        }
    }

    @Test
    fun givenOpenDatabase_whenQueried_shouldReturnResult() = testHttpSqlApplication { testContext ->
        val client = testContext.client
        val connectionInfo: HttpSqlConnectionInfo = runBlocking {
            client.get("/$PATH_CONNECTION_OPEN").body()
        }

        runBlocking {
            client.get("/$PATH_CONNECTION_CLOSE?$PARAM_CONNECTION_ID=${connectionInfo.connectionId}")
        }

        Assert.assertTrue(connectionInfo.connectionId != 0)
        verify(testContext.mockDataSource).connection
        verify(testContext.openedConnections.first()).close()
    }


    class PreparedStatementCtx(val sql: String, val response: PrepareStatementResponse)

    private fun testPreparedStatement(
        sql: String = """
                INSERT INTO RepEntity(reLastChangedBy, reLastChangeTime, reNumField, reString, reBoolean)
                VALUES(?, ?, ?, ?, ?)
                """,
        block: (HttpSqlTestContext, PreparedStatementCtx) -> Unit
    ) = testHttpSqlApplication { testContext ->
        val client = testContext.client
        val connectionInfo: HttpSqlConnectionInfo = runBlocking {
            client.get("/$PATH_CONNECTION_OPEN").body()
        }

        val preparedStatementResponse: PrepareStatementResponse = runBlocking {
            client.post("/$PATH_PREPARE_STATEMENT?$PARAM_CONNECTION_ID=${connectionInfo.connectionId}") {
                setBody(PrepareStatementRequest(sql, PreparedStatement.NO_GENERATED_KEYS))
                contentType(ContentType.Application.Json)
            }.body()
        }

        block(testContext, PreparedStatementCtx(sql, preparedStatementResponse))
    }

    val preparedStatementParamList = listOf(PreparedStatementParam(1, listOf("0"), TypesKmp.INTEGER),
        PreparedStatementParam(2, listOf("0"), TypesKmp.INTEGER),
        PreparedStatementParam(3, listOf("42"), TypesKmp.INTEGER),
        PreparedStatementParam(4, listOf("bob"), TypesKmp.LONGVARCHAR),
        PreparedStatementParam(5, listOf("false"), TypesKmp.BOOLEAN))

    private fun PreparedStatement.verifyParamListSet() {

        verify(this).setInt(1, 0)
        verify(this).setInt(2, 0)
        verify(this).setInt(3, 42)
        verify(this).setString(4, "bob")
        verify(this).setBoolean(5, false)
    }


    @Test
    fun givenOpenConnection_whenPrepareStatementCalled_andQueried_thenShouldCreateStatementAndSetParams() = testPreparedStatement { testContext, preparedStatementCtx ->
        val client = testContext.client

        runBlocking {
            client.post("/$PATH_PREPARED_STATEMENT_QUERY?$PARAM_PREPAREDSTATEMENT_ID=${preparedStatementCtx.response.preparedStatementId}") {
                setBody(PreparedStatementExecRequest(preparedStatementParamList))
                contentType(ContentType.Application.Json)
            }
        }

        verify(testContext.openedConnections.first()).prepareStatement(preparedStatementCtx.sql, PreparedStatement.NO_GENERATED_KEYS)
        val preparedStatement = testContext.preparedStatements.first()
        verify(preparedStatement).executeQuery()
        preparedStatement.verifyParamListSet()
    }

    @Test
    fun givenOpenConnection_whenPrepareStatementCalled_andUpdateCalled_thenShouldCreateStatementSetParamsAndCallUpdate() = testPreparedStatement { testContext, preparedStatementCtx ->
        val client = testContext.client

        runBlocking {
            client.post("/$PATH_PREPARED_STATEMENT_UPDATE?$PARAM_PREPAREDSTATEMENT_ID=${preparedStatementCtx.response.preparedStatementId}") {
                setBody(PreparedStatementExecRequest(preparedStatementParamList))
                contentType(ContentType.Application.Json)
            }
        }

        verify(testContext.openedConnections.first()).prepareStatement(preparedStatementCtx.sql, PreparedStatement.NO_GENERATED_KEYS)
        val preparedStatement = testContext.preparedStatements.first()
        verify(preparedStatement).executeUpdate()
        preparedStatement.verifyParamListSet()
    }


}