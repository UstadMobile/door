package com.ustadmobile.lib.annotationprocessor.core.replication

import com.ustadmobile.door.*
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.ext.waitUntilWithTimeout
import com.ustadmobile.door.replication.*
import com.ustadmobile.lib.annotationprocessor.core.VirtualHostScope
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.json.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.GlobalScope
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
import repdb.RepDb_ReplicationRunOnChangeRunner

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

    private lateinit var remoteNotificationDispatcher :ReplicationNotificationDispatcher

    private lateinit var localNotificationDispatcher: ReplicationNotificationDispatcher

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

        remoteRepDb = DatabaseBuilder.databaseBuilder(Any(), RepDb::class, "RepDbRemote")
            .build().also {
                it.clearAllTables()
            }

        localRepDb = DatabaseBuilder.databaseBuilder(Any(), RepDb::class, "RepDbLocal")
            .build().also {
                it.clearAllTables()
            }

        localDbRepo = localRepDb.asRepository(RepositoryConfig.repositoryConfig(Any(), "http://localhost:8089/",
            LOCAL_NODE_ID.toInt(), "secret", httpClient, okHttpClient))


        remoteVirtualHostScope = VirtualHostScope()

        remoteNotificationDispatcher = ReplicationNotificationDispatcher(
            remoteRepDb, RepDb_ReplicationRunOnChangeRunner(remoteRepDb), GlobalScope,
            RepDb::class.doorDatabaseMetadata())

        localNotificationDispatcher = ReplicationNotificationDispatcher(
            localRepDb, RepDb_ReplicationRunOnChangeRunner(remoteRepDb), GlobalScope,
            RepDb::class.doorDatabaseMetadata())


        remoteDi = DI {
            bind<RepDb>(tag = DoorTag.TAG_DB) with scoped(remoteVirtualHostScope).singleton {
                remoteRepDb
            }

            bind<ReplicationNotificationDispatcher>() with scoped(remoteVirtualHostScope).singleton {
                remoteNotificationDispatcher
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
            (localDbRepo as DoorDatabaseRepository).sendPendingReplications(jsonSerializer, RepEntity.TABLE_ID,
                REMOTE_NODE_ID)
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

    @Test
    fun givenMoreEntitiesThanInBatchCreatedRemotely_whenFetchPendingReplicationsCalled_thenShouldAllBePresentOnLocal() {
        remoteRepDb.repDao.insertDoorNode(DoorNode().apply {
            auth = "secret"
            nodeId = LOCAL_NODE_ID.toInt()
        })

        remoteRepDb.repDao.insertList((0..1500).map {
            RepEntity().apply {
                reString = "Fetch $it"
                reNumField = it
            }
        })

        runBlocking { remoteRepDb.repDao.updateReplicationTrackers() }

        runBlocking {
            (localDbRepo as DoorDatabaseRepository).fetchPendingReplications(jsonSerializer, RepEntity.TABLE_ID,
                REMOTE_NODE_ID)
        }

        Assert.assertEquals("All entities transferred", remoteRepDb.repDao.countEntities(),
            localRepDb.repDao.countEntities())
    }


    @Test
    fun givenMoreEntitiesThanInBatchCreatedLocally_whenSendPendingReplicationsCalled_thenAllShouldBePresentOnRemote() {
        localRepDb.repDao.insertDoorNode(DoorNode().apply {
            auth = "secret"
            nodeId = REMOTE_NODE_ID.toInt()
        })

        localRepDb.repDao.insertList((0..1500).map {
            RepEntity().apply {
                reString = "Hello World $it"
                reNumField = it
            }
        })

        runBlocking { localRepDb.repDao.updateReplicationTrackers() }

        runBlocking {
            (localDbRepo as DoorDatabaseRepository).sendPendingReplications(jsonSerializer, RepEntity.TABLE_ID,
                REMOTE_NODE_ID)
        }

        Assert.assertEquals("All entities transferred", localRepDb.repDao.countEntities(),
            remoteRepDb.repDao.countEntities())
    }


    //This needs a fix to invalidate the table so the live data will trigger
    //@Test
    fun givenReplicationSubscriptionEnabled_whenChangeMadeOnRemote_thenShouldTransferToLocal() {
        localRepDb.repDao.insertDoorNode(DoorNode().apply {
            auth = "secret"
            nodeId = REMOTE_NODE_ID.toInt()
        })

        remoteRepDb.repDao.insertDoorNode(DoorNode().apply {
            auth = "secret"
            nodeId = LOCAL_NODE_ID.toInt()
        })

        remoteRepDb.addChangeListener(ChangeListenerRequest(listOf(), remoteNotificationDispatcher))
        localRepDb.addChangeListener(ChangeListenerRequest(listOf(), localNotificationDispatcher))

        //Just create it - this will need a close
        ReplicationSubscriptionManager(jsonSerializer, localNotificationDispatcher,
            localDbRepo as DoorDatabaseRepository, GlobalScope, RepDb::class.doorDatabaseMetadata(), RepDb::class)


        val entity = RepEntity().apply {
            reString = "Subscribe and replicate"
            rePrimaryKey = remoteRepDb.repDao.insert(this)
        }

        val entityOnLocal = runBlocking {
            localRepDb.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(5000) {
                it != null
            }
        }

        Assert.assertNotNull(entityOnLocal)
    }

}