package com.ustadmobile.lib.annotationprocessor.core

import com.google.gson.Gson
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.routing.Routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import com.ustadmobile.door.*
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.dbVersionHeader
import db2.*
import db2.ExampleDatabase2.Companion.DB_VERSION
import io.ktor.application.ApplicationCall
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.*
import io.ktor.client.statement.HttpStatement
import io.ktor.gson.GsonConverter
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.server.engine.ApplicationEngine
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert
import org.kodein.di.bind
import org.kodein.di.bindings.Scope
import org.kodein.di.bindings.ScopeRegistry
import org.kodein.di.bindings.StandardScopeRegistry
import org.kodein.di.ktor.DIFeature
import org.kodein.di.registerContextTranslator
import org.kodein.di.scoped
import org.kodein.di.singleton
import java.util.concurrent.TimeUnit
import java.io.File
import java.nio.file.Files

class TestDbRoute  {

    lateinit var exampleDb: ExampleDatabase2

    lateinit var server: ApplicationEngine

    var tmpAttachmentsDir: File? = null

    private lateinit var httpClient: HttpClient

    class VirtualHostScope(): Scope<String> {

        private val activeHosts = mutableMapOf<String, ScopeRegistry>()

        override fun getRegistry(context: String): ScopeRegistry = activeHosts.getOrPut(context) { StandardScopeRegistry() }

    }

    //@Before
    fun setup() {
        exampleDb = DatabaseBuilder.databaseBuilder(Any(), ExampleDatabase2::class, "db1").build()
        exampleDb.clearAllTables()

        val virtualHostScope = VirtualHostScope()

        server = embeddedServer(Netty, port = 8089) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, GsonConverter())
                register(ContentType.Any, GsonConverter())
            }

            install(DIFeature) {
                bind<ExampleDatabase2>(tag = DoorTag.TAG_DB) with scoped(virtualHostScope).singleton {
                    exampleDb
                }

                bind<Gson>() with singleton { Gson() }

                registerContextTranslator { call: ApplicationCall -> "localhost" }
            }

            tmpAttachmentsDir = Files.createTempDirectory("TestDbRoute").toFile()
            install(Routing) {
                ExampleDatabase2_KtorRoute(true)
            }
        }

        server.start()
    }

    //@Before
    fun createHttpClient() {
        httpClient = HttpClient(OkHttp) {
            install(JsonFeature)

            engine {
                config {
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    //@After
    fun tearDown() {
        server.stop(0, 10000)
        httpClient.close()
    }

    //@Test
    fun givenDataInsertedOnPost_whenGetByUidCalled_thenShouldReturnSameObject() = runBlocking {
        val exampleEntity2 = ExampleEntity2(name = "bob", someNumber =  5L)

        val requestBuilder = HttpRequestBuilder()
        requestBuilder.body = exampleEntity2
        requestBuilder.url("http://localhost:8089/ExampleDatabase2/ExampleDao2/insertAndReturnId")
        requestBuilder.contentType(ContentType.Application.Json)
        requestBuilder.header(DoorConstants.HEADER_DBVERSION, DB_VERSION)

        exampleEntity2.uid = httpClient.post<Long>(requestBuilder)

        val entityFromServer = httpClient.get<ExampleEntity2>(
                "http://localhost:8089/ExampleDatabase2/ExampleDao2/findByUid?uid=${exampleEntity2.uid}") {
            header(DoorConstants.HEADER_DBVERSION, DB_VERSION)
        }
        assertEquals(exampleEntity2, entityFromServer, "Entity from server is retrieved OK")

        httpClient.close()
    }

    //@Test
    fun givenSyncableEntityInsertedOnServer_whenReceiptAcknowledged_thenNextRequestShouldReturnEmptyList() = runBlocking {
        val exampleSyncableEntity = ExampleSyncableEntity(esMcsn = 1, esNumber =  42)
        exampleSyncableEntity.esUid = exampleDb.exampleSyncableDao().insert(exampleSyncableEntity)

        var headers: Headers? = null
        val firstGetList = httpClient.get<HttpStatement>("http://localhost:8089/ExampleDatabase2/ExampleSyncableDao/findAll") {
            header("X-nid", 1)
            dbVersionHeader(exampleDb)
        }.execute { response ->
            headers = response.headers
            response.receive<List<ExampleSyncableEntity>>()
        }

        val reqId = headers?.get("X-reqid")!!.toInt()
        httpClient.get<Unit>("http://localhost:8089/ExampleDatabase2/ExampleSyncableDao/_updateExampleSyncableEntity_trkReceived?reqId=$reqId") {
            header("X-nid", 1)
            dbVersionHeader(exampleDb)
        }

        val secondGetList = httpClient.get<List<ExampleSyncableEntity>>("http://localhost:8089/ExampleDatabase2/ExampleSyncableDao/findAll") {
            header("X-nid", 1)
            dbVersionHeader(exampleDb)
        }

        Assert.assertEquals("First list has one item", 1, firstGetList.size)
        Assert.assertEquals("First entity in list is the item inserted", exampleSyncableEntity.esUid,
                firstGetList[0].esUid)
        Assert.assertTrue("Second response is empty after receipt was acknowledged",
                secondGetList.isEmpty())
    }

    //@Test
    fun givenSyncableEntityInsertedOnServer_whenReceiptIsNotAcknowledged_thenNextRequestShouldReturnSameListAgain() = runBlocking {
        val httpClient = HttpClient() {
            install(JsonFeature)
        }
        val exampleSyncableEntity = ExampleSyncableEntity(esMcsn = 1, esNumber =  42)
        exampleSyncableEntity.esUid = exampleDb.exampleSyncableDao().insert(exampleSyncableEntity)

        val firstGetList = httpClient.get<List<ExampleSyncableEntity>> {
            url{
                takeFrom("http://localhost:8089/")
                path("ExampleDatabase2", "ExampleSyncableDao", "findAll")
                parameter("x", 1)
                dbVersionHeader(exampleDb)
            }
            header("X-nid", 1)
        }

        val secondGetList = httpClient.get<List<ExampleSyncableEntity>>("http://localhost:8089/ExampleDatabase2/ExampleSyncableDao/findAll") {
            header("X-nid", 1)
            dbVersionHeader(exampleDb)
        }

        Assert.assertEquals("First list has one item", 1, firstGetList.size)
        Assert.assertEquals("First entity in list is the item inserted", exampleSyncableEntity.esUid,
                firstGetList[0].esUid)
        Assert.assertEquals("Second response has one item when response not acknowledged",
                1, secondGetList.size)
    }



}