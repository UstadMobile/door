package com.ustadmobile.door.replication

import app.cash.turbine.test
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.ext.asRepository
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.http.DoorHttpServerConfig
import com.ustadmobile.door.ktor.routes.ReplicationRoute
import com.ustadmobile.door.test.initNapierLog
import com.ustadmobile.door.util.systemTimeInMillis
import db3.ExampleDb3
import db3.ExampleEntity3
import io.ktor.client.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class PushIntegrationTest {

    lateinit var server: ApplicationEngine

    private lateinit var serverDb: ExampleDb3

    private val serverNodeId = 1L

    private lateinit var clientDb: ExampleDb3

    private lateinit var httpClient: HttpClient

    private lateinit var okHttpClient: OkHttpClient

    private val clientNodeId = 2L

    private val json = Json {
        encodeDefaults = true
    }

    @BeforeTest
    fun setup() {
        initNapierLog()
        serverDb = DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", serverNodeId)
            .build()
        serverDb.clearAllTables()

        clientDb = DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", clientNodeId)
            .build()
        clientDb.clearAllTables()

        okHttpClient = OkHttpClient.Builder().build()
        httpClient = HttpClient {
            @Suppress("RemoveRedundantQualifierName", "RedundantSuppression") //Ensure clarity between client and server
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(json = json)
            }
        }

        val serverConfig = DoorHttpServerConfig(json)
        server = embeddedServer(Netty, 8094) {
            routing {
                route(DoorRepositoryReplicationClient.REPLICATION_PATH) {
                    ReplicationRoute(serverConfig) { serverDb }
                }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        server.stop()
        serverDb.close()
        clientDb.close()
    }

    private fun ExampleDb3.asClientNodeRepository() = asRepository(
        RepositoryConfig.repositoryConfig(
            context = Any(),
            endpoint = "http://localhost:8094/",
            nodeId = clientNodeId,
            auth = "secret",
            httpClient = httpClient,
            okHttpClient = okHttpClient
        )
    )



    @Test
    fun givenEntityWithOutgoingReplicationCreatedOnServerBeforeClientConnects_whenClientConnects_thenShouldReplicateToClient() {
        server.start()
        val insertedEntity = ExampleEntity3(lastUpdatedTime = systemTimeInMillis(), cardNumber = 123)
        runBlocking {
            serverDb.withDoorTransactionAsync {
                insertedEntity.eeUid = serverDb.exampleEntity3Dao.insertAsync(insertedEntity)
                serverDb.exampleEntity3Dao.insertOutgoingReplication(insertedEntity.eeUid, clientNodeId)
            }
        }

        val clientRepo = clientDb.asClientNodeRepository()

        runBlocking {
            clientRepo.exampleEntity3Dao.findByUidAsFlow(insertedEntity.eeUid).filter {
                it != null
            }.test(timeout = 5.seconds, name = "Entity is replicated from server to client as expected") {
                assertNotNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
        clientRepo.close()
    }

    @Test
    fun givenEntityWithOutgoingReplicationCreatedOnClientBeforeClientConnects_whenClientCnonects_thenShouldReplicateToServer() {
        server.start()
        val insertedEntity = ExampleEntity3(lastUpdatedTime = systemTimeInMillis(), cardNumber = 123)
        runBlocking {
            clientDb.withDoorTransactionAsync {
                insertedEntity.eeUid = clientDb.exampleEntity3Dao.insertAsync(insertedEntity)
                clientDb.exampleEntity3Dao.insertOutgoingReplication(insertedEntity.eeUid, serverNodeId)
            }
        }

        val clientRepo = clientDb.asClientNodeRepository()

        runBlocking {
            serverDb.exampleEntity3Dao.findByUidAsFlow(insertedEntity.eeUid).filter {
                it != null
            }.test(timeout = 5.seconds, name = "Entity is replicated from client to server as expected") {
                assertNotNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        clientRepo.close()
    }

    @Test(timeout = 10000)
    fun givenBlankClientDatabase_whenEntityCreatedOnClientAfterConnection_thenShouldReplicateToServer() {
        server.start()
        val clientRepo = clientDb.asClientNodeRepository()
        val clientRepoClientState = (clientRepo as DoorDatabaseRepository).clientState
        runBlocking {
            clientRepoClientState.filter { it.initialized }.first()

            //This is not ideal, but we want to be sure that the first connection has been made. That isn't something that
            // any normal use case would need to know
            delay(500)

            val insertedEntity = ExampleEntity3(lastUpdatedTime = systemTimeInMillis(), cardNumber = 123)

            clientRepo.withDoorTransactionAsync {
                insertedEntity.eeUid = clientRepo.exampleEntity3Dao.insertAsync(insertedEntity)
            }

            serverDb.exampleEntity3Dao.findByUidAsFlow(insertedEntity.eeUid).filter {
                it != null
            }.test(timeout = 5.seconds, name = "Entity created after connection is replicated from client to server as expected") {
                assertNotNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
        clientRepo.close()
    }

    @Test
    fun givenBlankServerDatabase_whenEntityCreatedOnServerAfterConnection_thenShouldReplicateToClient() {
        server.start()
        val clientRepo = clientDb.asClientNodeRepository()
        val clientRepoClientState = (clientRepo as DoorDatabaseRepository).clientState

        runBlocking {
            clientRepoClientState.filter { it.initialized }.first()

            //This is not ideal, but we want to be sure that the first connection has been made. That isn't something that
            // any normal use case would need to know
            delay(500)

            val insertedEntity = ExampleEntity3(lastUpdatedTime = systemTimeInMillis(), cardNumber = 123)

            serverDb.withDoorTransactionAsync {
                insertedEntity.eeUid = serverDb.exampleEntity3Dao.insertAsync(insertedEntity)
                serverDb.exampleEntity3Dao.insertOutgoingReplication(insertedEntity.eeUid, clientNodeId)
            }

            clientDb.exampleEntity3Dao.findByUidAsFlow(insertedEntity.eeUid).filter {
                it != null
            }.test(timeout = 5.seconds, name = "Entity created after connection is replicated from server to client as expected") {
                assertNotNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        clientRepo.close()
    }

    @Test(timeout = 10000)
    fun givenBlankClientDatabase_whenEntityCreatedOnClientBeforeServerStarts_thenShouldReplicateToClientWhenConnected() {
        val clientRepo = clientDb.asClientNodeRepository()
        runBlocking {
            val insertedEntity = ExampleEntity3(lastUpdatedTime = systemTimeInMillis(), cardNumber = 123)

            clientRepo.withDoorTransactionAsync {
                insertedEntity.eeUid = clientRepo.exampleEntity3Dao.insertAsync(insertedEntity)
            }

            server.start()

            serverDb.exampleEntity3Dao.findByUidAsFlow(insertedEntity.eeUid).filter {
                it != null
            }.test(timeout = 5.seconds, name = "Entity created after connection is replicated from client to server as expected") {
                assertNotNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
        clientRepo.close()
    }
}