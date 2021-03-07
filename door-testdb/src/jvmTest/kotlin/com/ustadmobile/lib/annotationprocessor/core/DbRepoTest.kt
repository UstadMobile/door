package com.ustadmobile.lib.annotationprocessor.core

import com.github.aakira.napier.DebugAntilog
import com.github.aakira.napier.LogLevel
import com.github.aakira.napier.Napier
import com.google.gson.Gson
import com.nhaarman.mockitokotlin2.*
import com.ustadmobile.door.*
import com.ustadmobile.door.DoorDatabaseRepository.Companion.STATUS_CONNECTED
import db2.AccessGrant
import db2.ExampleDatabase2
import db2.ExampleDatabase2_KtorRoute
import db2.ExampleSyncableEntity
import db2.ExampleAttachmentEntity
import io.ktor.application.ApplicationCall
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.features.ContentNegotiation
import io.ktor.gson.GsonConverter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.netty.handler.codec.http.HttpResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.rules.TemporaryFolder
import org.kodein.di.*
import org.kodein.di.ktor.DIFeature
import org.sqlite.SQLiteDataSource
import java.io.File
import javax.sql.DataSource
import kotlin.test.assertEquals
import java.net.URI
import java.nio.file.Paths
import com.ustadmobile.door.attachments.retrieveAttachment
import com.ustadmobile.door.ext.*


class DbRepoTest {

    var serverDb : ExampleDatabase2? = null

    lateinit var serverRepo: ExampleDatabase2

    lateinit var clientDb: ExampleDatabase2

    lateinit var clientDb2: ExampleDatabase2

    var server: ApplicationEngine? = null

    lateinit var httpClient: HttpClient

    lateinit var tmpAttachmentsDir: File

    lateinit var tmpServerAttachmentsDir: File

    lateinit var mockUpdateNotificationManager: ServerUpdateNotificationManager

    lateinit var serverDi: DI

    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    @Before
    fun setup() {
        tmpAttachmentsDir = temporaryFolder.newFolder("testclientattachments")
        tmpServerAttachmentsDir = temporaryFolder.newFolder("testserverattachments")
        mockUpdateNotificationManager = mock {}

        if(!Napier.isEnable(LogLevel.DEBUG, null)) {
            Napier.base(DebugAntilog())
        }

        Napier.i("Hello Napier")
    }


    fun createSyncableDaoServer(di: DI) = embeddedServer(Netty, 8089) {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, GsonConverter())
            register(ContentType.Any, GsonConverter())
        }


        install(DIFeature) {
            extend(di)

        }

        install(Routing) {
            ExampleDatabase2_KtorRoute(true)
        }

        //install(CallLogging)
    }

    fun setupClientAndServerDb(updateNotificationManager: ServerUpdateNotificationManager = mockUpdateNotificationManager) {
        try {
            val virtualHostScope = TestDbRoute.VirtualHostScope()
            serverDi = DI {
                bind<ExampleDatabase2>(tag = DoorTag.TAG_DB) with scoped(virtualHostScope).singleton {
                    DatabaseBuilder.databaseBuilder(Any(), ExampleDatabase2::class, "ExampleDatabase2")
                            .build().also {
                                it.clearAllTables()
                            }
                }

                bind<ExampleDatabase2>(tag = DoorTag.TAG_REPO) with scoped(virtualHostScope).singleton {
                    val db: ExampleDatabase2 = instance(tag = DoorTag.TAG_DB)
                    val repo = db.asRepository(Any(), "http://localhost/", "", httpClient,
                            tmpServerAttachmentsDir.absolutePath, updateNotificationManager)
                    ServerChangeLogMonitor(db, repo as DoorDatabaseRepository)
                    repo
                }

                bind<Gson>() with singleton { Gson() }

                bind<String>(tag = DoorTag.TAG_ATTACHMENT_DIR) with scoped(virtualHostScope).singleton {
                    tmpServerAttachmentsDir.absolutePath
                }

                bind<DataSource>() with factory { dbName: String ->
                    SQLiteDataSource().also {
                        it.url = "jdbc:sqlite:${dbName}.sqlite"
                    }
                }

                bind<ServerUpdateNotificationManager>() with scoped(virtualHostScope).singleton {
                    updateNotificationManager
                }

                registerContextTranslator { call: ApplicationCall -> "localhost" }
            }

            serverDb = serverDi.on("localhost").direct.instance(tag = DoorTag.TAG_DB)
            serverRepo = serverDi.on("localhost").direct.instance(tag = DoorTag.TAG_REPO)

            clientDb = DatabaseBuilder.databaseBuilder(Any(), ExampleDatabase2::class, "db1")
                    .build().also {
                        it.clearAllTables()
                    }

            clientDb2 = DatabaseBuilder.databaseBuilder(Any(), ExampleDatabase2::class, "db2")
                    .build().also {
                        it.clearAllTables()
                    }

            server = createSyncableDaoServer(serverDi)
            server!!.start(wait = false)
        }catch(e: Exception) {
            e.printStackTrace()
            throw e
        }

    }

    @Before
    fun createHttpClient(){
        httpClient = HttpClient(OkHttp) {
            install(JsonFeature)
        }
    }

    @After
    fun tearDown() {
        server?.stop(0, 10000)
        httpClient.close()
    }

    @Test
    fun givenSyncableEntityDao_whenGetSyncableListCalled_shouldMakeHttpRequestAndInsertResult() {
        val mockServer = MockWebServer()

        val firstResponseList = listOf(ExampleSyncableEntity(esUid = 42, esMcsn = 5))
        mockServer.enqueue(MockResponse()
                .setBody(Gson().toJson(firstResponseList))
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("X-reqid", 50))
        mockServer.enqueue(MockResponse()
                .setResponseCode(204)
                .setBody(""))
        mockServer.start()

        val db = DatabaseBuilder.databaseBuilder(Any(), ExampleDatabase2::class, "db1").build()
        db.clearAllTables()
        val dbRepo = db.asRepository(Any(), mockServer.url("/").toString(),
                "", httpClient, null)
                .asConnectedRepository()

        val clientNodeId = (dbRepo as DoorDatabaseSyncRepository).clientId
        val repo = dbRepo.exampleSyncableDao()
        val repoResult = repo.findAll()

        val firstRequest = mockServer.takeRequest()
        Assert.assertEquals("First http call was to get list", "/ExampleDatabase2/ExampleSyncableDao/findAll",
                firstRequest.path)
        Assert.assertEquals("After list was received from server, it was inserted into local db",
                firstResponseList[0].esNumber,
                db.exampleSyncableDao().findByUid(firstResponseList[0].esUid)!!.esNumber)
        Assert.assertEquals("Request contained client id", clientNodeId,
                firstRequest.getHeader("X-nid")?.toInt())

        val secondRequest = mockServer.takeRequest()
        Assert.assertEquals("Repo made request to acknowledge receipt of entities",
                "/ExampleDatabase2/ExampleSyncableDao/_ackExampleSyncableEntityReceived",
                secondRequest.path)
    }

    @Test
    fun givenEntityCreatedOnMaster_whenClientGetCalled_thenShouldReturnAndBeCopiedToServer() {
        setupClientAndServerDb()
        val exampleSyncableEntity = ExampleSyncableEntity(esNumber = 42)
        exampleSyncableEntity.esUid = serverRepo!!.exampleSyncableDao().insert(exampleSyncableEntity)

        val clientRepo = clientDb!!.asRepository(Any(),
                "http://localhost:8089/", "token", httpClient)
                .asConnectedRepository()

        val entityFromServer = clientRepo.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid)
        Assert.assertNotNull("Entity came back from server using repository", entityFromServer)
        val entityInClientDb = clientDb!!.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid)
        Assert.assertEquals("Entity is in client db and has some number property",
                42, entityInClientDb!!.esNumber)
    }

    @Test
    fun givenEntityUpdatedOnServer_whenClientGetCalled_thenLocalEntityShouldBeUpdated() {
        setupClientAndServerDb()
        val serverDb = this.serverDb!!
        val clientDb = this.clientDb!!
        val exampleSyncableEntity = ExampleSyncableEntity(esNumber = 42)
        exampleSyncableEntity.esUid = serverRepo.exampleSyncableDao().insert(exampleSyncableEntity)

        val clientRepo = clientDb.asRepository<ExampleDatabase2>(Any(), "http://localhost:8089/",
                "token", httpClient)
                .asConnectedRepository<ExampleDatabase2>()

        val entityFromServerBeforeChange = clientRepo.exampleSyncableDao()
                .findByUid(exampleSyncableEntity.esUid)

        serverRepo.exampleSyncableDao().updateNumberByUid(exampleSyncableEntity.esUid, 43)
        val entityFromServerAfterChange = clientRepo.exampleSyncableDao()
                .findByUid(exampleSyncableEntity.esUid)

        Assert.assertEquals("Got original entity from DAO before change", 42,
                entityFromServerBeforeChange!!.esNumber)

        Assert.assertEquals("After change, got new entity", 43,
                entityFromServerAfterChange!!.esNumber)

        Assert.assertEquals("Copy in database is updated copy", 43,
                clientDb.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid)!!.esNumber)
    }


    @Test
    fun givenEntityCreatedOnServer_whenRepoSyncCalled_thenShouldBePresentOnClient() {
        setupClientAndServerDb(ServerUpdateNotificationManagerImpl())
        val serverDb = this.serverDb!!
        val clientDb = this.clientDb!!
        runBlocking {
            val clientId = clientDb.exampleSyncableDao().getSyncNode()!!.nodeClientId
            val exampleSyncableEntity = ExampleSyncableEntity(esUid = 50, esNumber = 42)

            serverRepo.accessGrantDao().insert(AccessGrant().apply {
                tableId = 42
                deviceId = clientId
                entityUid = exampleSyncableEntity.esUid
            })

            serverRepo.exampleSyncableDao().insert(exampleSyncableEntity)


            val clientRepo = clientDb.asRepository(Any(), "http://localhost:8089/",
                    "token", httpClient, useClientSyncManager = true)
                    .asConnectedRepository()

            //Wait for the entity to land on the client
            clientDb.waitUntil(10000, listOf("ExampleSyncableEntity")) {
                clientDb.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid) != null
            }

            //Wait for the server to delete the update notifications
            delay(1000)
            serverDb.waitUntil(10000, listOf("UpdateNotification")) {
                serverDb.updateNotificationTestDao().getUpdateNotificationsForDevice(clientId).isEmpty()
            }

            Assert.assertNotNull("Entity is in client database after sync",
                    clientDb.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid))

            val updateNotificationList = serverDb.updateNotificationTestDao().getUpdateNotificationsForDevice(clientId)
            Napier.d("UpdateNotificationList=${updateNotificationList.joinToString {"${it.pnDeviceId - it.pnTableId}" } }")
            //TODO: the remaining line is flaky only when run with all other tests, not when it is run on it's own.
            // this is likely because the repo and database itself (inc the clientupdatemanager) is never closed
            // hence previous tests seem to interfere. This has not caused an issue on production
//            Assert.assertEquals("Processed updateNotifications have been deleted", 0,
//                    updateNotificationList.size)
        }
    }

    @Test
    fun givenEntityCreatedOnClient_whenRepoSyncCalled_thenShouldBePresentOnServer() {
        setupClientAndServerDb()
        val serverDb = this.serverDb!!
        val clientDb = this.clientDb!!
        val clientRepo = clientDb.asRepository(Any(),"http://localhost:8089/",
                "token", httpClient, useClientSyncManager = true).asConnectedRepository()
        runBlocking {
            val exampleSyncableEntity = ExampleSyncableEntity(esNumber = 42)
            exampleSyncableEntity.esUid = clientRepo.exampleSyncableDao().insert(exampleSyncableEntity)

            serverDb.waitUntil(5000, listOf("ExampleSyncableEntity")) {
                serverDb.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid) != null
            }

            Assert.assertNotNull("Entity is in client database after sync",
                    serverDb.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid))
        }
    }

    @Test
    fun givenEntityCreatedOnClientWithUtf8Chars_whenRepoSyncCalled_thenShouldBeCorrectOnServer() {
        setupClientAndServerDb()
        val serverDb = this.serverDb!!
        val clientDb = this.clientDb!!
        val clientRepo = clientDb.asRepository(Any(),"http://localhost:8089/",
                "token", httpClient, useClientSyncManager = true).asConnectedRepository()
        val entityName = "سلام"

        runBlocking {
            val exampleSyncableEntity = ExampleSyncableEntity(esNumber =  50, esName = entityName)
            exampleSyncableEntity.esUid = clientRepo.exampleSyncableDao().insert(exampleSyncableEntity)

            serverDb.waitUntil(5000, listOf("ExampleSyncableEntity")) {
                serverDb.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid) != null
            }

            val entityInServer = serverDb.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid)
            val entityOnClient = clientDb.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid)
            Assert.assertNotNull("Entity found on server", entityInServer)
            Assert.assertEquals("Name was saved correctly on client",
                    entityOnClient!!.esName, entityName)
            Assert.assertEquals("Name matches", entityName, entityInServer!!.esName)
        }
    }

    @Test
    fun givenEntityCreatedOnServerWithUtf8Chars_whenRepoSyncCalled_thenShouldBeCorrectOnClient() {
        setupClientAndServerDb()
        val serverDb = this.serverDb!!
        val clientDb = this.clientDb!!
        val clientRepo = clientDb.asRepository<ExampleDatabase2>(Any(),"http://localhost:8089/",
                "token", httpClient).asConnectedRepository<ExampleDatabase2>()

        val entityName = "سلام"

        runBlocking {
            val exampleSyncableEntity = ExampleSyncableEntity(esNumber =  50, esName = entityName)
            exampleSyncableEntity.esUid = serverRepo.exampleSyncableDao().insert(exampleSyncableEntity)

            val entityFromRepoClient = clientRepo.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid)
            Assert.assertNotNull("Got entity on server via client repo", entityFromRepoClient)
            Assert.assertEquals("Name is encoded correctly", entityName, entityFromRepoClient!!.esName)
        }
    }

    @Test
    fun givenEntityCreatedOnClient_whenUpdatedOnServerAndSyncCalled_thenShouldBeUpdatedOnClient() {
        setupClientAndServerDb(ServerUpdateNotificationManagerImpl())
        val serverDb = this.serverDb!!
        val clientDb = this.clientDb!!
        val clientRepo = clientDb.asRepository(Any(), "http://localhost:8089/",
                "token", httpClient, useClientSyncManager = true).asConnectedRepository()
        runBlocking {
            Napier.i("=====Create Initial Entity on client======")
            val exampleSyncableEntity = ExampleSyncableEntity(esUid = 420, esNumber = 42)
            serverRepo.accessGrantDao().insert(AccessGrant().apply {
                tableId = 42
                deviceId = (clientRepo as DoorDatabaseSyncRepository).clientId
                entityUid = exampleSyncableEntity.esUid
            })
            clientRepo.exampleSyncableDao().insert(exampleSyncableEntity)

            //wait for the entity to hit the server
            serverDb.waitUntil(5000, listOf("ExampleSyncableEntity")) {
                serverDb.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid) != null
            }

            val updateNotificationsBefore = serverDb.exampleSyncableDao().findAllUpdateNotifications()

            val entityOnServerAfterSync = serverDb.exampleSyncableDao()
                    .findByUid(exampleSyncableEntity.esUid)

            Napier.i("=====Waited for entity on server: got $entityOnServerAfterSync======")

            delay(500)

            val updateNotificationAfter = serverDb.exampleSyncableDao().findAllUpdateNotifications()

            Napier.i("======= Performing update on server======")
            serverRepo.exampleSyncableDao().updateAsync(entityOnServerAfterSync!!.apply {
                esNumber = 52
                esLcb = 2
            })

            clientDb.waitUntil(5000, listOf("ExampleSyncableEntity")) {
                clientDb.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid)?.esNumber == 52
            }

            Napier.i("======= Waited for entity update to occur on client ======")

            Assert.assertNotNull("Entity was synced to server after being created on client",
                    entityOnServerAfterSync)
            Assert.assertEquals("Syncing after change made on server, value on client is udpated",
                    52, clientDb.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid)?.esNumber)
        }
    }

    @Test
    fun givenEntityCreatedOnServer_whenUpdatedOnClientAndSyncCalled_thenShouldBeUpdatedOnServer() {
        setupClientAndServerDb(ServerUpdateNotificationManagerImpl())
        val serverDb = this.serverDb!!
        val clientDb = this.clientDb!!
        val clientRepo = clientDb.asRepository<ExampleDatabase2>(Any(),
                "http://localhost:8089/", "token",
                httpClient, useClientSyncManager = true).asConnectedRepository<ExampleDatabase2>()
        runBlocking {
            val exampleSyncableEntity = ExampleSyncableEntity(esNumber = 42)
            exampleSyncableEntity.esUid = serverRepo.exampleSyncableDao().insert(exampleSyncableEntity)

            serverRepo.accessGrantDao().insert(AccessGrant().apply {
                tableId = 42
                deviceId = (clientRepo as DoorDatabaseSyncRepository).clientId
                entityUid = exampleSyncableEntity.esUid
            })

            clientDb.waitUntil(5000, listOf("ExampleSyncableEntity")) {
                clientDb.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid) != null
            }


            val entityOnClientAfterSync = clientDb.exampleSyncableDao()
                    .findByUid(exampleSyncableEntity.esUid)



            clientRepo.exampleSyncableDao().updateNumberByUid(exampleSyncableEntity.esUid, 53)
            serverDb.waitUntil(10000, listOf("ExampleSyncableEntity")) {
                serverDb.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid)?.esNumber == 53
            }


            Assert.assertNotNull("Entity was synced to client after being created on server",
                    entityOnClientAfterSync)
            Assert.assertEquals("Syncing after change made on server, value on server is udpated",
                    53, serverDb.exampleSyncableDao().findByUid(exampleSyncableEntity.esUid)!!.esNumber)
        }
    }

    @Test
    fun givenSyncableEntityWithListParam_whenGetCalled_thenShouldBeReturned(){
        setupClientAndServerDb()
        val serverDb = this.serverDb!!
        val clientDb = this.clientDb!!
        val clientRepo = clientDb.asRepository<ExampleDatabase2>(Any(), "http://localhost:8089/", "token",
                httpClient).asConnectedRepository<ExampleDatabase2>()
        val e1 = ExampleSyncableEntity(esNumber = 42)
        var e2 = ExampleSyncableEntity(esNumber = 43)
        e1.esUid = serverRepo.exampleSyncableDao().insert(e1)
        e2.esUid = serverRepo.exampleSyncableDao().insert(e2)

        runBlocking {
            val entitiesFromListParam = clientRepo.exampleSyncableDao().findByListParam(
                    listOf(42, 43))
            assertEquals(2, entitiesFromListParam.size, "Got expected results from list param query")
        }

    }

    @Test
    fun givenBlankEntityInsertedAndSynced_whenLocallyUpdatedAndSynced_shouldUpdateServer() {
        setupClientAndServerDb(ServerUpdateNotificationManagerImpl())
        val serverDb = this.serverDb!!
        val clientDb = this.clientDb!!
        val clientRepo = clientDb.asRepository(Any(),"http://localhost:8089/",
                "token", httpClient, useClientSyncManager = true).asConnectedRepository()

        runBlocking {
            //1. Create a blank entity. Insert it.
            val client1 = ExampleSyncableEntity().apply {
                esUid = 420
            }
            serverRepo.accessGrantDao().insert(AccessGrant().apply {
                this.tableId = ExampleSyncableEntity.TABLE_ID
                this.entityUid = 420
                this.deviceId = (clientRepo as DoorDatabaseSyncRepository).clientId
            })

            clientRepo.exampleSyncableDao().insert(client1)

            //2. Lets sync happen  - Verify blank entity is on server
            serverDb.waitUntil(5000, listOf("ExampleSyncableEntity")) {
                serverDb.exampleSyncableDao().findByUid(client1.esUid) != null
            }

            val server1 = serverDb.exampleSyncableDao().findByUid(client1.esUid)
            Assert.assertEquals("Server got the entity OK", client1.esUid, server1?.esUid)

            //3. Make changes and update entity.
            client1.esName = "Hello"
            client1.esNumber = 42
            clientRepo.exampleSyncableDao().updateAsync(client1)
            val client2 = clientDb.exampleSyncableDao().findByUid(client1.esUid)
            Assert.assertEquals("Client updated locally OK", "Hello", client2?.esName)


            //4. Let sync happen - Verify update on server.
            serverDb.waitUntil(5000, listOf("ExampleSyncableEntity")) {
                serverDb.exampleSyncableDao().findByUid(client1.esUid)?.esName == "Hello"
            }
            val server2 = serverDb.exampleSyncableDao().findByUid(client1.esUid)
            Assert.assertEquals("Name matches", "Hello", server2?.esName)
        }
    }

    @Test
    fun givenOldClient_whenRequestMade_thenShouldReceive400Forbidden() = runBlocking {
        setupClientAndServerDb()
        var httpStatusErr: HttpStatusCode? = null
        try {
            val response = httpClient.get<HttpResponse>("http://localhost:8089/ExampleDatabase2/ExampleSyncableDao/findAllLive") {
                header("X-nid", 1234)
                header(DoorConstants.HEADER_DBVERSION, 0)
            }
        }catch(e: ClientRequestException) {
            httpStatusErr = e.response.status
        }


        Assert.assertEquals(HttpStatusCode.BadRequest, httpStatusErr)
    }

    @Test
    fun givenEmptyDatabase_whenChangeMadeOnServer_thenOnNewUpdateNotificationShouldBeCalledAndNotificationEntityShouldBeInserted()  {
        mockUpdateNotificationManager = spy(ServerUpdateNotificationManagerImpl())
        setupClientAndServerDb(mockUpdateNotificationManager)

        val testUid = 42L

        val serverDb: ExampleDatabase2 by serverDi.on("localhost").instance(tag = DoorTag.TAG_DB)
        val serverRepo: ExampleDatabase2 by serverDi.on("localhost").instance(tag = DoorTag.TAG_REPO)
        println(serverRepo)

        serverRepo.accessGrantDao().insert(AccessGrant().apply {
            deviceId = 57
            tableId = 42
            entityUid = testUid
        })

        val exampleEntity = ExampleSyncableEntity().apply {
            esUid = testUid
            esName = "Hello Notification"
            serverRepo.exampleSyncableDao().insert(this)
        }

        verify(mockUpdateNotificationManager, timeout(5000 )).onNewUpdateNotifications(
                argWhere { it.any { it.pnTableId ==  42 && it.pnDeviceId == 57} })
    }

    //Test the realtime update notification setup
    @Test
    fun givenClientSubscribedToUpdates_whenChangeMadeOnServer_thenShouldUpdateClient() {
        mockUpdateNotificationManager = spy(ServerUpdateNotificationManagerImpl())
        setupClientAndServerDb(mockUpdateNotificationManager)


        val serverDb: ExampleDatabase2 by serverDi.on("localhost").instance(tag = DoorTag.TAG_DB)

        val clientDb = this.clientDb!!
        val clientRepo = clientDb.asRepository(Any(),"http://localhost:8089/",
                "token", httpClient).asConnectedRepository<ExampleDatabase2>()

        val clientSyncManager = ClientSyncManager(clientRepo as DoorDatabaseSyncRepository,
            2, STATUS_CONNECTED, httpClient)


        val testUid = 42L

        //wait for subscription to take effect.
        Thread.sleep(2000)

        serverRepo.accessGrantDao().insert(AccessGrant().apply {
            deviceId = clientRepo.clientId
            tableId = 42
            entityUid = testUid
        })

        val exampleEntity = ExampleSyncableEntity().apply {
            esUid = testUid
            esName = "Hello Notification"
            serverRepo.exampleSyncableDao().insert(this)
        }

        runBlocking {
            clientDb.waitUntil(10000, listOf("ExampleSyncableEntity")) {
                clientDb.exampleSyncableDao().findByUid(testUid) != null
            }
        }

        Assert.assertNotNull("Entity udpated on server was automagically brought to client",
            clientDb.exampleSyncableDao().findByUid(testUid))
    }

    @Test
    fun givenEntityPresentOnServerFirst_whenNewClientConnects_thenShouldBePresent() {
        mockUpdateNotificationManager = spy(ServerUpdateNotificationManagerImpl())
        setupClientAndServerDb(mockUpdateNotificationManager)

        val serverDb: ExampleDatabase2 by serverDi.on("localhost").instance(tag = DoorTag.TAG_DB)

        val testUid = 42L

        val exampleEntity = ExampleSyncableEntity().apply {
            esUid = testUid
            esName = "Hello Notification"
            publik = true
            serverRepo.exampleSyncableDao().insert(this)
        }

        Napier.i("==== Initializing client =====")

        val clientDb = this.clientDb!!

        //TODO: This should be closed to be sure that it does not interfere with the next test etc.
        val clientRepo = clientDb.asRepository(Any(), "http://localhost:8089/",
                "token", httpClient, useClientSyncManager = true)
                .asConnectedRepository()

        Napier.i("==== Waiting for entity to arrive on client =====")

        runBlocking {
            clientDb.waitUntil(10000, listOf("ExampleSyncableEntity")) {
                clientDb.exampleSyncableDao().findByUid(exampleEntity.esUid) != null
            }

            Assert.assertNotNull("Entity is in client database after sync",
                    clientDb.exampleSyncableDao().findByUid(exampleEntity.esUid))
        }
    }


    @Test
    fun givenEntityCreatedOnFirstClient_whenUpdatedOnFirstClient_thenShouldUpdateOnSecondClient() {
        mockUpdateNotificationManager = spy(ServerUpdateNotificationManagerImpl())
        setupClientAndServerDb(mockUpdateNotificationManager)

        val serverDb: ExampleDatabase2 by serverDi.on("localhost").instance(tag = DoorTag.TAG_DB)

        val testUid = 42L

        Napier.i("==== Initializing client =====")

        //TODO: This should be closed to be sure that it does not interfere with the next test etc.
        val clientRepo1 = clientDb.asRepository(Any(), "http://localhost:8089/",
                "token", httpClient, useClientSyncManager = true)
                .asConnectedRepository()

        val clientRepo2 = clientDb2.asRepository(Any(), "http://localhost:8089/",
                "token", httpClient, useClientSyncManager = true)
                .asConnectedRepository()

        //make access grants so that the notification will be dispatched
        listOf(clientRepo1, clientRepo2).forEach { repo ->
            serverRepo.accessGrantDao().insert(AccessGrant().apply {
                tableId = ExampleSyncableEntity.TABLE_ID
                deviceId = (repo as DoorDatabaseSyncRepository).clientId
                entityUid = testUid
            })
        }

        val exampleEntity = ExampleSyncableEntity().apply {
            esUid = testUid
            esName = "Hello Notification"
            publik = true
        }
        clientRepo1.exampleSyncableDao().insert(exampleEntity)

        //wait for the entity to arrive on the second client
        runBlocking {
            clientDb2.waitUntil(10000, listOf("ExampleSyncableEntity")) {
                clientDb2.exampleSyncableDao().findByUid(exampleEntity.esUid) != null
            }
        }

        val entityOnServer = serverDb.exampleSyncableDao().findByUid(exampleEntity.esUid)
        val entityOnClient2AfterCreation = clientDb2.exampleSyncableDao().findByUid(exampleEntity.esUid)

        runBlocking {
            clientRepo1.exampleSyncableDao().updateAsync(exampleEntity.apply {
                esName = "Hello Updated Notification"
            })
        }

        runBlocking {
            clientDb2.waitUntil(10000, listOf("ExampleSyncableEntity")) {
                clientDb2.exampleSyncableDao().findByUid(exampleEntity.esUid)?.esName == "Hello Updated Notification"
            }
        }

        val entityOnServerAfterUpdate = serverDb.exampleSyncableDao().findByUid(exampleEntity.esUid)
        println(entityOnServerAfterUpdate)

        Assert.assertNotNull("Entity reached server after creation", entityOnServer)
        Assert.assertNotNull("Entity reached client2 after creation", entityOnClient2AfterCreation)
        Assert.assertEquals("Entity was updated on client2 after update on client1",
            exampleEntity.esName, clientDb2.exampleSyncableDao().findByUid(exampleEntity.esUid)?.esName)
    }

    @Test
    fun givenEntityCreatedFirst_whenAccessGrantEntityCreatedAfter_thenShouldTriggerSyncOfEntity() {
        mockUpdateNotificationManager = spy(ServerUpdateNotificationManagerImpl())
        setupClientAndServerDb(mockUpdateNotificationManager)
        val testUid = 42L

        val serverDb: ExampleDatabase2 by serverDi.on("localhost").instance(tag = DoorTag.TAG_DB)

        val exampleEntity = ExampleSyncableEntity().apply {
            esUid = testUid
            esName = "Hello Notification"
            publik = true
        }

        Napier.i("==== Initializing client =====")

        val clientDb = this.clientDb!!

        //TODO: This should be closed to be sure that it does not interfere with the next test etc.
        val clientRepo = clientDb.asRepository(Any(), "http://localhost:8089/",
                "token", httpClient, useClientSyncManager = true)
                .asConnectedRepository()

        Napier.i("==== Waiting for entity to arrive on client =====")


        //Wait for the client to connect
        Thread.sleep(1000)

        serverRepo.exampleSyncableDao().insert(exampleEntity)

        val clientId = clientDb.exampleSyncableDao().getSyncNode()!!.nodeClientId

        serverRepo.accessGrantDao().insert(AccessGrant().apply {
            tableId = 42
            deviceId = clientId
            entityUid = exampleEntity.esUid
        })

        runBlocking {
            clientDb.waitUntil(10000, listOf("ExampleSyncableEntity")) {
                clientDb.exampleSyncableDao().findByUid(exampleEntity.esUid) != null
            }

            Assert.assertNotNull("Entity is in client database after sync",
                    clientDb.exampleSyncableDao().findByUid(exampleEntity.esUid))
        }
    }

    @Test
    fun givenEntityWithAttachmentUri_whenInserted_thenAttachmentIsStored() {
        setupClientAndServerDb()
        val clientRepo = clientDb!!.asRepository(Any(),
                "http://localhost:8089/", "token", httpClient, tmpAttachmentsDir.absolutePath)
                .asConnectedRepository()

        val destFile = temporaryFolder.newFile()
        this::class.java.getResourceAsStream("/testfile1.png").writeToFile(destFile)

        val attachmentEntity = ExampleAttachmentEntity().apply {
            eaAttachmentUri = destFile.toURI().toString()
            eaUid  = clientRepo.exampleAttachmentDao().insert(this)
        }

        val storedUri = runBlocking {
            (clientRepo as DoorDatabaseRepository).retrieveAttachment(attachmentEntity.eaAttachmentUri!!)
        }
        val storedFile = storedUri.toFile()//   Paths.get(URI(storedUri)).toFile()

        Assert.assertTrue("Stored entity exists", storedFile.exists())
    }

    @Test
    fun givenEntityWithAttachmentsUri_whenInsertedThenUpdated_thenOldAttachmentIsDeleted() {
        setupClientAndServerDb()
        val clientRepo = clientDb!!.asRepository(Any(),
                "http://localhost:8089/", "token", httpClient, tmpAttachmentsDir.absolutePath)
                .asConnectedRepository()

        val destFile = temporaryFolder.newFile()
        this::class.java.getResourceAsStream("/testfile1.png").writeToFile(destFile)

        val attachmentEntity = ExampleAttachmentEntity().apply {
            eaAttachmentUri = destFile.toURI().toString()
            eaUid  = clientRepo.exampleAttachmentDao().insert(this)
        }

        val firstStoredUri = runBlocking {
            (clientRepo as DoorDatabaseRepository).retrieveAttachment(attachmentEntity.eaAttachmentUri!!)
        }

        val destFile2 = temporaryFolder.newFile()
        this::class.java.getResourceAsStream("/cat-pic0.jpg").writeToFile(destFile2)
        attachmentEntity.eaAttachmentUri = destFile2.toURI().toString()

        clientRepo.exampleAttachmentDao().update(attachmentEntity)

        val firstStoredFile = firstStoredUri.toFile() //Paths.get(URI(firstStoredUri)).toFile()
        Assert.assertFalse("Old file does not exist anymore", firstStoredFile.exists())
    }

    @Test
    fun givenEntityWithAttachmentUri_whenInsertedOnClient_thenDataShouldUploadToServer() {
        mockUpdateNotificationManager = spy(ServerUpdateNotificationManagerImpl())
        setupClientAndServerDb(mockUpdateNotificationManager)


        val destFile = temporaryFolder.newFile()
        this::class.java.getResourceAsStream("/testfile1.png").writeToFile(destFile)

        val clientRepo = clientDb.asRepository(Any(), "http://localhost:8089/",
                "token", httpClient, attachmentsDir = tmpAttachmentsDir.absolutePath,
                useClientSyncManager = true)
                .asConnectedRepository()


        val attachmentEntity = ExampleAttachmentEntity().apply {
            eaAttachmentUri = destFile.toURI().toString()
            eaUid  = clientRepo.exampleAttachmentDao().insert(this)
        }

        runBlocking {
            serverDb!!.waitUntil(10000, listOf("ExampleAttachmentEntity")) {
                serverDb!!.exampleAttachmentDao().findByUid(attachmentEntity.eaUid) != null
            }
        }

        val entityOnServer = serverDb!!.exampleAttachmentDao().findByUid(attachmentEntity.eaUid)
        val serverAttachmentUri = runBlocking {
            (serverRepo as DoorDatabaseRepository).retrieveAttachment(entityOnServer!!.eaAttachmentUri!!)
        }

        val serverFile =  serverAttachmentUri.toFile() //Paths.get(URI(serverAttachmentUri)).toFile()
        Assert.assertTrue("Attachment file exists on server", serverFile.exists())
        Assert.assertArrayEquals("Attachment data is equal on server and client",
            destFile.md5Sum, serverFile.md5Sum)
    }


    @Test
    fun givenEntityWithAttachmentUri_whenInsertedOnServer_thenDataShouldDownloadToClient() {
        mockUpdateNotificationManager = spy(ServerUpdateNotificationManagerImpl())
        setupClientAndServerDb(mockUpdateNotificationManager)

        val destFile = temporaryFolder.newFile()
        this::class.java.getResourceAsStream("/testfile1.png").writeToFile(destFile)

        val attachmentEntity = ExampleAttachmentEntity().apply {
            eaAttachmentUri = destFile.toURI().toString()
            eaUid  = serverRepo.exampleAttachmentDao().insert(this)
        }

        val clientRepo = clientDb.asRepository(Any(), "http://localhost:8089/",
                "token", httpClient, attachmentsDir = tmpAttachmentsDir.absolutePath,
                useClientSyncManager = true)
                .asConnectedRepository()

        runBlocking {
            clientDb.waitUntil(10000, listOf("ExampleAttachmentEntity")) {
                clientDb.exampleAttachmentDao().findByUid(attachmentEntity.eaUid) != null
            }
        }

        val entityOnClient = clientDb.exampleAttachmentDao().findByUid(attachmentEntity.eaUid)
        val clientAttachmentUri = runBlocking {
            (clientRepo as DoorDatabaseRepository).retrieveAttachment(entityOnClient!!.eaAttachmentUri!!)
        }

        val clientFile = clientAttachmentUri.toFile() //Paths.get(URI(clientAttachmentUri)).toFile()

        Assert.assertTrue("Attachment data was downloaded to file on client",
                clientFile.exists())
        Assert.assertArrayEquals("Client data is the same as the original file",
                destFile.md5Sum, clientFile.md5Sum)

    }

    @Test
    fun givenMoreEntitiesOnServerThanBatchSize_whenConnected_thenShouldSyncAllEntities() {
        mockUpdateNotificationManager = spy(ServerUpdateNotificationManagerImpl())
        setupClientAndServerDb(mockUpdateNotificationManager)

        val syncableEntityList = (0 .. 3000).map {
            ExampleSyncableEntity().apply {
                esNumber = it
                esName = "entity $it"
                publik = true
            }
        }
        serverRepo.exampleSyncableDao().insertList(syncableEntityList)
        val allEntitiesOnServer = serverDb!!.exampleSyncableDao().findAll()

        val clientRepo = clientDb.asRepository(Any(), "http://localhost:8089/",
                "token", httpClient, attachmentsDir = tmpAttachmentsDir.absolutePath,
                useClientSyncManager = true)
                .asConnectedRepository()

        runBlocking {
            clientDb.waitUntil(10000, listOf("ExampleSyncableEntity")) {
                clientDb.exampleSyncableDao().findAll().size == syncableEntityList.size
            }
        }

        val entitiesInClientDb = clientDb.exampleSyncableDao().findAll()
        Assert.assertEquals("Same number of entities in client db locally as server db",
            syncableEntityList.size, entitiesInClientDb.size)

        Assert.assertTrue("All entities from server are in client",
                allEntitiesOnServer.all { serverEntity ->
                    entitiesInClientDb.any { it.esUid == serverEntity.esUid  }
                })
    }

    @Test
    fun givenMoreEntitiesOnClientThanBatchSize_whenConnected_thenShouldSyncAll() {
        mockUpdateNotificationManager = spy(ServerUpdateNotificationManagerImpl())
        setupClientAndServerDb(mockUpdateNotificationManager)

        val syncableEntityList = (0 .. 3000).map {
            ExampleSyncableEntity().apply {
                esNumber = it
                esName = "entity $it"
                publik = true
            }
        }


        val clientRepo = clientDb.asRepository(Any(), "http://localhost:8089/",
                "token", httpClient, attachmentsDir = tmpAttachmentsDir.absolutePath,
                useClientSyncManager = true)
                .asConnectedRepository()

        clientRepo.exampleSyncableDao().insertList(syncableEntityList)

        val entitiesInClientDb = clientDb.exampleSyncableDao().findAll()


        runBlocking {
            serverDb!!.waitUntil(10000, listOf("ExampleSyncableEntity")){
                serverDb!!.exampleSyncableDao().findAll().size == syncableEntityList.size
            }
        }

        val entitiesInServerDb = serverDb!!.exampleSyncableDao().findAll()
        Assert.assertEquals("Number of entities on server is the same as client",
                syncableEntityList.size, entitiesInServerDb.size)
        Assert.assertTrue("All entities from client db are in server db",
                entitiesInClientDb.all { clientEntity ->
            entitiesInServerDb.any { it.esUid == clientEntity.esUid }
        })
    }

}