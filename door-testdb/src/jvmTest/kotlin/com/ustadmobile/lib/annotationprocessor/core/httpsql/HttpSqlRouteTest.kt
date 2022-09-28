package com.ustadmobile.lib.annotationprocessor.core.httpsql

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.room.RoomDatabase
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.junit.Test
import repdb.RepDb
import com.ustadmobile.door.httpsql.HttpSql
import com.ustadmobile.door.httpsql.HttpSqlConnectionInfo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.gson.gson
import io.ktor.server.application.install
import io.ktor.http.ContentType
import io.ktor.serialization.gson.GsonConverter
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.junit.Assert
import repdb.RepEntity


class HttpSqlRouteTest {

    class HttpSqlTestContext(val db: RepDb, val client: HttpClient, val json: Json)

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
                    HttpSql(db, {true}, json)
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
            val connectionInfo: HttpSqlConnectionInfo = testContext.client.get("/open").body()
            val queryResultText = testContext.client.post(
                "/statementQuery?connectionId=${connectionInfo.connectionId}"
            ){
                setBody("SELECT reString FROM RepEntity LIMIT 1")
            }.bodyAsText()

            val queryResult = testContext.json.decodeFromString(JsonObject.serializer(), queryResultText)
            val reString = queryResult["rows"]?.jsonArray?.get(0)?.jsonObject?.get("reString")?.jsonPrimitive?.content
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
            val connectionInfo: HttpSqlConnectionInfo = testContext.client.get("/open").body()
            val updateResultText = testContext.client.post(
                "/statementUpdate?connectionId=${connectionInfo.connectionId}"
            ) {
                setBody("UPDATE RepEntity SET reNumField = reNumField + 10 WHERE rePrimaryKey = 41")
            }.bodyAsText()

            val updatesJson = testContext.json.decodeFromString(JsonObject.serializer(), updateResultText)
            val numUpdates = updatesJson["updates"]?.jsonPrimitive?.int ?: 0
            Assert.assertTrue("Response indicates one updated performed",  numUpdates >= 1)

            val entityInDbUpdated = testContext.db.repDao.findByUid(repEntity.rePrimaryKey)
            Assert.assertEquals("Entity was updated in DB", 60,
                entityInDbUpdated?.reNumField ?: -1)
        }
    }

}