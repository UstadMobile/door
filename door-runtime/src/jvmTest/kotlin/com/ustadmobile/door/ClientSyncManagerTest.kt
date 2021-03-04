package com.ustadmobile.door

import com.github.aakira.napier.DebugAntilog
import com.github.aakira.napier.Napier
import com.nhaarman.mockitokotlin2.*
import com.ustadmobile.door.DoorDatabaseRepository.Companion.STATUS_CONNECTED
import com.ustadmobile.door.daos.ISyncHelperEntitiesDao
import com.ustadmobile.door.entities.TableSyncStatus
import com.ustadmobile.door.entities.UpdateNotification
import com.ustadmobile.door.util.systemTimeInMillis
import io.ktor.application.ApplicationCall
import io.ktor.client.HttpClient
import io.ktor.features.ContentNegotiation
import io.ktor.gson.GsonConverter
import io.ktor.http.ContentType
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.kodein.di.bind
import org.kodein.di.bindings.Scope
import org.kodein.di.bindings.ScopeRegistry
import org.kodein.di.bindings.StandardScopeRegistry
import org.kodein.di.ktor.DIFeature
import org.kodein.di.registerContextTranslator
import org.kodein.di.scoped
import org.kodein.di.singleton
import java.util.concurrent.atomic.AtomicBoolean
import com.ustadmobile.door.ktor.respondUpdateNotificationReceived
import com.ustadmobile.door.ktor.respondUpdateNotifications
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.routing.get
import io.ktor.routing.routing
import org.junit.Before


class ClientSyncManagerTest {

    @Before
    fun setup() {
        Napier.takeLogarithm()
        Napier.base(DebugAntilog())
    }

    @Test
    fun givenValidRepo_whenRepoChanges_thenShouldUpdateStatusTableAndCallSync() {
        val exampleTableId = 42
        val tableChanged = AtomicBoolean(false)
        val mockSyncHelperEntitiesDao = mock<ISyncHelperEntitiesDao> {
            on { findTablesToSync() }.thenAnswer {
                if(tableChanged.get()) {
                    listOf(TableSyncStatus(exampleTableId, systemTimeInMillis(), 0))
                }else {
                    listOf()
                }
            }
        }
        val mockRepo = mock<DoorDatabaseSyncRepository> {
            on { tableIdMap }.thenReturn(mapOf("Example" to exampleTableId))
            on { syncHelperEntitiesDao}.thenReturn(mockSyncHelperEntitiesDao)
        }

        val clientSyncManager = ClientSyncManager(mockRepo, 2, STATUS_CONNECTED,
                HttpClient())

        //wait for the initial load
        verify(mockSyncHelperEntitiesDao, timeout(4000)).findTablesToSync()

        tableChanged.set(true)

        clientSyncManager.onTableChanged("Example")
        verifyBlocking(mockSyncHelperEntitiesDao, timeout(4000)) { updateTableSyncStatusLastChanged(eq(exampleTableId), any()) }
        verifyBlocking(mockRepo, timeout(4000)) { mockRepo.sync(argWhere { exampleTableId in it })}
    }


    class VirtualHostScope(): Scope<String> {

        private val activeHosts = mutableMapOf<String, ScopeRegistry>()

        override fun getRegistry(context: String): ScopeRegistry = activeHosts.getOrPut(context) { StandardScopeRegistry() }

    }

    @Test
    fun givenValidRepo_whenSubscriptionNotificationIsSentByServer_thenShouldCallSync() {
        val updateListeners = mutableListOf<UpdateNotificationListener>()

        val exampleTableId = 42
        val updateNotificationId = 5000L
        val notificationTime = systemTimeInMillis()

        val mockSyncHelperEntitiesDao = mock<ISyncHelperEntitiesDao> {
            on { findTablesToSync() }.thenReturn(listOf())
        }

        val mockRepo = mock<DoorDatabaseSyncRepository>() {
            on { tableIdMap }.thenReturn(mapOf("Example" to exampleTableId))
            on { endpoint }.thenReturn("http://localhost:8089/")
            on { clientId }.thenReturn(57)
            on { syncHelperEntitiesDao }.thenReturn(mockSyncHelperEntitiesDao)
            on { dbPath }.thenReturn("ExampleDatabase")
        }

        val mockServerSyncHelperEntitiesDao = mock<ISyncHelperEntitiesDao> {
            onBlocking { findPendingUpdateNotifications(any()) }.thenReturn(
                    listOf(UpdateNotification(updateNotificationId, 1234, exampleTableId, notificationTime)))
        }

        val mockServerRepo = mock<DoorDatabaseSyncRepository> {
            on { tableIdMap }.thenReturn(mapOf("Example" to exampleTableId))
            on { syncHelperEntitiesDao }.thenReturn(mockServerSyncHelperEntitiesDao)
        }

        val mockUpdateNotificationManager = mock<ServerUpdateNotificationManager> {
            on { addUpdateNotificationListener(any(), any()) }.thenAnswer {
                updateListeners.add(it.arguments[1] as UpdateNotificationListener)
            }
        }

        val virtualHostScope = VirtualHostScope()
        val server = embeddedServer(Netty, 8089) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, GsonConverter())
                register(ContentType.Any, GsonConverter())
            }

            install(DIFeature) {
                bind<ServerUpdateNotificationManager>() with scoped(virtualHostScope).singleton {
                    mockUpdateNotificationManager
                }

                registerContextTranslator { call: ApplicationCall -> "localhost" }
            }

            routing {
                get("ExampleDatabase/UpdateNotification/update-events") {
                    call.respondUpdateNotifications(mockServerRepo)
                }

                get("ExampleDatabase/UpdateNotification/update-ack") {
                    call.respondUpdateNotificationReceived(mockServerRepo)
                }
            }

        }
        server.start()

        val httpClient = HttpClient()

        val clientSyncManager = ClientSyncManager(mockRepo, 2, STATUS_CONNECTED,
                httpClient)

        runBlocking {
            verifyBlocking(mockSyncHelperEntitiesDao, timeout(30000)) {
                updateTableSyncStatusLastChanged(eq(exampleTableId), any())
            }

            verifyBlocking(mockServerSyncHelperEntitiesDao, timeout(10000)) {
                deleteUpdateNotification(mockRepo.clientId, exampleTableId, notificationTime)
            }
        }

    }

}