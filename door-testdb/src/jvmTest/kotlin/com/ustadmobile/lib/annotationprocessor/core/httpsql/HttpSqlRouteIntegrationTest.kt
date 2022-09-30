package com.ustadmobile.lib.annotationprocessor.core.httpsql

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.httpsql.*
import com.ustadmobile.door.room.RoomDatabase
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.junit.Test
import repdb.RepDb
import com.ustadmobile.door.httpsql.HttpSqlPaths.KEY_ROWS
import com.ustadmobile.door.httpsql.HttpSqlPaths.KEY_UPDATES
import com.ustadmobile.door.httpsql.HttpSqlPaths.PARAM_CONNECTION_ID
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_CONNECTION_OPEN
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_PREPARE_STATEMENT
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_STATEMENT_QUERY
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_STATEMENT_UPDATE
import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.TypesKmp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.gson
import io.ktor.server.application.install
import io.ktor.serialization.gson.GsonConverter
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.junit.Assert
import repdb.RepEntity


class HttpSqlRouteIntegrationTest {

    class HttpSqlTestContext(val db: RepDb, val client: HttpClient, val json: Json) {
        suspend fun openPreparedStatement(sql: String) : PrepareStatementResponse{
            val connectionInfo: HttpSqlConnectionInfo = client.get("/connection/open").body()
            return client.post("/connection/${connectionInfo.connectionId}/preparedStatement/create") {
                setBody(PrepareStatementRequest(sql = sql, generatedKeys = PreparedStatement.NO_GENERATED_KEYS))
                contentType(ContentType.Application.Json)
            }.body()
        }
    }

    private fun <T: RoomDatabase> testHttpSqlApplication(
        testBlock: TestApplicationBuilder.(HttpSqlTestContext) -> Unit,
    ) {
        val db = DatabaseBuilder.databaseBuilder(RepDb::class, "jdbc:sqlite:build/tmp/repdb.sqlite")
            .build().also {
                it.clearAllTables()
            }

        val json = Json { encodeDefaults = true }
        testApplication {
            environment {
                config = MapApplicationConfig("ktor.environment" to "test")
            }

            application {
                routing {
                    HttpSql({ db }, {true}, json)
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

            testBlock(HttpSqlTestContext(db, client, json))
        }
    }

    @Test
    fun givenEntityInDb_whenQueriedUsingHttpSqlRoute_thenShouldReturnResult() = testHttpSqlApplication<RepDb> { testContext ->
        val repEntity = RepEntity().apply {
            rePrimaryKey = 41L
            reString = "HelloHttp"
        }
        testContext.db.repDao.insert(repEntity)
        runBlocking {
            val connectionInfo: HttpSqlConnectionInfo = testContext.client.get("/connection/open").body()
            val queryResultText = testContext.client.post(
                "/connection/${connectionInfo.connectionId}/statement/query"
            ){
                setBody("SELECT reString FROM RepEntity LIMIT 1")
            }.bodyAsText()

            val queryResult = testContext.json.decodeFromString(JsonObject.serializer(), queryResultText)
            val reString = queryResult[KEY_ROWS]?.jsonArray?.get(0)?.jsonObject?.get("reString")?.jsonPrimitive?.content
            Assert.assertEquals("Value returned from http matches value inserted into database",
                "HelloHttp", reString)
        }
    }


    @Test
    fun givenOpenDatabase_whenUpdated_thenShouldTakeEffectOnDb() = testHttpSqlApplication<RepDb> { testContext ->
        val repEntity = RepEntity().apply {
            rePrimaryKey = 41L
            reNumField = 50
        }
        testContext.db.repDao.insert(repEntity)

        runBlocking {
            val connectionInfo: HttpSqlConnectionInfo = testContext.client.get("/connection/open").body()
            val updateResultText = testContext.client.post(
                "/connection/${connectionInfo.connectionId}/statement/update"
            ) {
                setBody("UPDATE RepEntity SET reNumField = reNumField + 10 WHERE rePrimaryKey = 41")
            }.bodyAsText()

            val updatesJson = testContext.json.decodeFromString(JsonObject.serializer(), updateResultText)
            val numUpdates = updatesJson[KEY_UPDATES]?.jsonPrimitive?.int ?: 0
            Assert.assertTrue("Response indicates one updated performed",  numUpdates >= 1)

            val entityInDbUpdated = testContext.db.repDao.findByUid(repEntity.rePrimaryKey)
            Assert.assertEquals("Entity was updated in DB", 60,
                entityInDbUpdated?.reNumField ?: -1)
        }
    }

    @Test
    fun givenOpenDatabase_whenPreparedStatementCreatedThenQueryExecuted_thenShouldReturnResult() = testHttpSqlApplication<RepDb> { testContext ->
        val repEntity = RepEntity().apply {
            rePrimaryKey = 41L
            reNumField = 50
        }
        testContext.db.repDao.insert(repEntity)

        runBlocking {
            val prepStatementResponse = testContext.openPreparedStatement("SELECT * FROM RepEntity WHERE reNumField < ?")
            val queryBodyStr = testContext.client.post("/connection/${prepStatementResponse.connectionId}" +
                    "/preparedStatement/${prepStatementResponse.preparedStatementId}/query"
            ) {
                setBody(PreparedStatementExecRequest(listOf(PreparedStatementParam(1, listOf("100"), TypesKmp.INTEGER))))
                contentType(ContentType.Application.Json)
            }.bodyAsText()
            val queryJsonResponse = testContext.json.decodeFromString(JsonObject.serializer(), queryBodyStr)
            val resultArray = queryJsonResponse[KEY_ROWS]?.jsonArray
            Assert.assertEquals("Got one row", 1, resultArray?.size ?: 0)
            Assert.assertEquals("Got result back",
                41L, resultArray!!.first().jsonObject["rePrimaryKey"]!!.jsonPrimitive.long)
        }
    }

    @Test
    fun givenOpenDatabase_whenPreparedStatementCreatedThenUpdateExecuted_shouldTakeEffect() = testHttpSqlApplication<RepDb> { testContext ->
        val repEntity = RepEntity().apply {
            rePrimaryKey = 41L
            reNumField = 50
        }
        testContext.db.repDao.insert(repEntity)

        runBlocking {
            val prepStatementResponse = testContext.openPreparedStatement("UPDATE RepEntity SET reNumField = ?")
            val respBodyStr = testContext.client.post("/connection/${prepStatementResponse.connectionId}" +
                    "/preparedStatement/${prepStatementResponse.preparedStatementId}/update") {
                setBody(PreparedStatementExecRequest(listOf(PreparedStatementParam(1, listOf("60"), TypesKmp.INTEGER))))
                contentType(ContentType.Application.Json)
            }.bodyAsText()
            val queryJsonResp = testContext.json.decodeFromString(JsonObject.serializer(), respBodyStr)

            val repEntityInDb = testContext.db.repDao.findByUid(41L)
            Assert.assertEquals("RepEntity was updated", 60, repEntityInDb?.reNumField ?: -1)
            val numUpdates = queryJsonResp[KEY_UPDATES]?.jsonPrimitive?.int ?: 0
            Assert.assertTrue("Query response returns num rows updated", numUpdates > 0)
        }
    }

}