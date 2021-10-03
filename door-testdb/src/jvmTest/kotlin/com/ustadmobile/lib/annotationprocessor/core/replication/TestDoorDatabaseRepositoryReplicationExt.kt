package com.ustadmobile.lib.annotationprocessor.core.replication

import com.ustadmobile.door.*
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.replication.doorReplicationRoute
import com.ustadmobile.door.replication.fetchPendingReplications
import com.ustadmobile.door.replication.sendPendingReplications
import com.ustadmobile.lib.annotationprocessor.core.VirtualHostScope
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.json.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.kodein.di.*
import org.kodein.di.ktor.DIFeature
import org.kodein.type.erased
import repdb.RepDb
import repdb.RepEntity

class TestDoorDatabaseRepositoryReplicationExt  {

    private lateinit var remoteServer: ApplicationEngine

    private lateinit var remoteDi: DI

    private lateinit var remoteRepDb: RepDb

    private lateinit var localRepDb: RepDb

    private lateinit var localDbRepo: RepDb

    private lateinit var remoteVirtualHostScope: VirtualHostScope

    private lateinit var httpClient: HttpClient

    private lateinit var okHttpClient: OkHttpClient

    private lateinit var jsonSerializer: Json

    val REMOTE_NODE_ID = 1234L

    val LOCAL_NODE_ID = 1330L

    @Before
    fun setup() {
        jsonSerializer = Json {
            encodeDefaults = true
        }

        okHttpClient = OkHttpClient.Builder().build()

        httpClient = HttpClient(OkHttp) {
            install(JsonFeature)
            engine {
                preconfigured = okHttpClient
            }
        }

        remoteVirtualHostScope = VirtualHostScope()

        remoteRepDb = DatabaseBuilder.databaseBuilder(Any(), RepDb::class, "RepDbRemote")
            .build().also {
                it.clearAllTables()
            }

        remoteDi = DI {
            bind<RepDb>(tag = DoorTag.TAG_DB) with scoped(remoteVirtualHostScope).singleton {
                remoteRepDb
            }

            registerContextTranslator { call: ApplicationCall -> "localhost" }
        }

        remoteServer = embeddedServer(Netty, 8089) {
            install(DIFeature){
                extend(remoteDi)
            }

            routing {
                doorReplicationRoute(erased(), RepDb::class, jsonSerializer)
            }
        }
        remoteServer.start()

        localRepDb = DatabaseBuilder.databaseBuilder(Any(), RepDb::class, "RepDbLocal")
            .build().also {
                it.clearAllTables()
            }



        localDbRepo = localRepDb.asRepository(RepositoryConfig.repositoryConfig(Any(), "http://localhost:8089/",
            LOCAL_NODE_ID.toInt(), "secret", httpClient, okHttpClient))
    }

    @After
    fun tearDown() {
        remoteServer.stop(1000, 1000)
    }

    /**
     *
     */
    @Test
    fun givenEntityCreatedLocally_whenSendPendingReplicationsCalled_thenShouldBePresenterOnRemote() {
        localRepDb.repDao.insertDoorNode(DoorNode().apply {
            auth = "secret"
            nodeId = REMOTE_NODE_ID.toInt()
        })

        val repEntity = RepEntity().apply {
            reString = "Hello World"
            rePrimaryKey = localRepDb.repDao.insert(this)
        }


        runBlocking { localRepDb.repDao.updateReplicationTrackers() }

        runBlocking {
            (localDbRepo as DoorDatabaseRepository).sendPendingReplications(jsonSerializer, REMOTE_NODE_ID,
                RepEntity.TABLE_ID)
        }

        Assert.assertNotNull("entity now on remote", remoteRepDb.repDao.findByUid(repEntity.rePrimaryKey))
    }

    @Test
    fun givenEntityCreatedRemotely_whenFetchPendingReplicationsCalled_thenShouldBePresentOnLocal() {
        remoteRepDb.repDao.insertDoorNode(DoorNode().apply {
            auth = "secret"
            nodeId = LOCAL_NODE_ID.toInt()
        })

        val repEntity = RepEntity().apply {
            reString = "Fetch"
            rePrimaryKey = remoteRepDb.repDao.insert(this)
        }

        runBlocking { remoteRepDb.repDao.updateReplicationTrackers() }

        runBlocking {
            (localDbRepo as DoorDatabaseRepository).fetchPendingReplications(jsonSerializer, RepEntity.TABLE_ID,
                REMOTE_NODE_ID)
        }

        Assert.assertNotNull("Entity now on local", localRepDb.repDao.findByUid(repEntity.rePrimaryKey))
    }

}