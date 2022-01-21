package com.ustadmobile.door

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import com.ustadmobile.door.sse.DoorEventListener
import com.ustadmobile.door.sse.DoorEventSource
import com.ustadmobile.door.sse.DoorServerSentEvent
import io.ktor.application.call
import io.ktor.client.features.*
import io.ktor.response.respondTextWriter
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

class DoorEventSourceTest {

    private lateinit var okHttpClient: OkHttpClient

    private lateinit var mockRepoConfig: RepositoryConfig

    @Before
    fun setup() {
        Napier.takeLogarithm()
        Napier.base(DebugAntilog())

        okHttpClient = OkHttpClient.Builder().build()
        mockRepoConfig = mock {
            on { okHttpClient }.thenReturn(okHttpClient)
        }
    }

    @After
    fun tearDown() {
        okHttpClient.dispatcher.executorService.shutdown()
    }


    private fun embeddedEventSourceServer(eventChannel: Channel<DoorServerSentEvent>): NettyApplicationEngine {
        return embeddedServer(Netty, 8094) {
            routing {
                get("subscribe") {
                    call.respondTextWriter(contentType = io.ktor.http.ContentType.Text.EventStream) {
                        for(notification in eventChannel) {
                            notification.data.lines().forEach { line ->
                                write("data: ${notification.stringify()}\n")
                            }
                            write("\n")
                            flush()
                        }
                    }

                }
            }
        }
    }


    @Suppress("BlockingMethodInNonBlockingContext")
    @Test
    fun givenEventSourceCreated_whenEventSent_thenOnMessageIsCalled() {
        val eventChannel = Channel<DoorServerSentEvent>(Channel.UNLIMITED)

        val testServer = embeddedEventSourceServer(eventChannel).also {
            it.start()
        }

        val eventListener = mock<DoorEventListener> {}

        val eventSource = DoorEventSource(mockRepoConfig, "http://localhost:8094/subscribe", eventListener)
        eventChannel.trySend(DoorServerSentEvent("42", "UPDATE", "Hello World"))
        verify(eventListener, timeout(5000)).onMessage(argWhere { it.id == "42" })

        GlobalScope.launch {
            delay(2000)
            eventChannel.trySend(DoorServerSentEvent("50", "UPDATE", "Hello World"))
        }

        verify(eventListener, timeout(20000)).onMessage(argWhere { it.id == "50" })
        eventSource.close()

        testServer.stop(1000, 1000)
    }

    @Test
    fun givenEventSourceCreated_whenServerInterrupted_thenShouldRetryAndReconnect() {
        val eventChannel = Channel<DoorServerSentEvent>(Channel.UNLIMITED)

        val testServer = embeddedEventSourceServer(eventChannel).also {
            it.start()
        }

        val eventListener = mock<DoorEventListener> {}

        val eventSource = DoorEventSource(mockRepoConfig, "http://localhost:8094/subscribe", eventListener)
        eventChannel.trySend(DoorServerSentEvent("42", "UPDATE", "Hello World"))
        verify(eventListener, timeout(5000)).onMessage(argWhere { it.id == "42" })
        testServer.stop(1000, 1000)

        Thread.sleep(2000)
        val eventChannel2 = Channel<DoorServerSentEvent>(Channel.UNLIMITED)
        val testServer2 = embeddedEventSourceServer(eventChannel2).also {
            it.start()
        }
        eventChannel2.trySend(DoorServerSentEvent("43", "UPDATE", "Hello World"))
        verify(eventListener, timeout(5000)).onMessage(argWhere { it.id == "43" })
        testServer2.stop(1000, 1000)
        eventSource.close()
    }

}