package com.ustadmobile.door

import com.github.aakira.napier.DebugAntilog
import com.github.aakira.napier.Napier
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.ustadmobile.door.daos.ISyncHelperEntitiesDao
import com.ustadmobile.door.entities.UpdateNotification
import com.ustadmobile.door.ktor.respondUpdateNotifications
import com.ustadmobile.door.util.systemTimeInMillis
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.GsonConverter
import io.ktor.http.ContentType
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.kodein.di.bind
import org.kodein.di.ktor.DIFeature
import org.kodein.di.registerContextTranslator
import org.kodein.di.scoped
import org.kodein.di.singleton

/**
 * This is here to facilitate manual testing - e.g. timeouts on receiving entities through different
 * proxies etc.
 */
class RespondUpdateNotificationsTest {

    //@Test
    fun setup() {
        Napier.base(DebugAntilog())
        val updateListeners = mutableListOf<UpdateNotificationListener>()
        val exampleTableId = 42

        val mockUpdateNotificationManager = mock<ServerUpdateNotificationManager> {
            on { addUpdateNotificationListener(any(), any()) }.thenAnswer {
                updateListeners.add(it.arguments[1] as UpdateNotificationListener)
            }
            on { removeUpdateNotificationListener(any(), any())}.thenAnswer {
                updateListeners.remove(it.arguments[1] as UpdateNotificationListener)
            }
        }

        val mockSyncHelperEntitiesDao = mock<ISyncHelperEntitiesDao> {
            onBlocking { findPendingUpdateNotifications(any()) }.thenReturn(
                    listOf(UpdateNotification(5000, 1234, exampleTableId, systemTimeInMillis())))
        }

        val mockServerRepo = mock<DoorDatabaseSyncRepository> {
            on { tableIdMap }.thenReturn(mapOf("Example" to exampleTableId))
            on { syncHelperEntitiesDao}.thenReturn(mockSyncHelperEntitiesDao)
        }


        val virtualHostScope = ClientSyncManagerTest.VirtualHostScope()
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
                get("ExampleDatabaseSyncDao/_subscribe") {
                    call.respondUpdateNotifications(mockServerRepo)
                }
            }

        }
        server.start()

        runBlocking {
            for ( i in 1..1000) {
                delay(500)
                updateListeners.forEach { it.onNewUpdate(UpdateNotification(pnUid =  i.toLong(),
                        pnDeviceId = 50, pnTableId = 42)) }
            }
        }

    }

}