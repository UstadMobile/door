package com.ustadmobile.door.ktor

import io.github.aakira.napier.Napier
import com.ustadmobile.door.DoorDatabaseSyncRepository
import com.ustadmobile.door.UpdateNotificationListener
import com.ustadmobile.door.ServerUpdateNotificationManager
import com.ustadmobile.door.entities.UpdateNotification
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.asDoorServerSentEvent
import com.ustadmobile.door.sse.DoorServerSentEvent
import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.response.cacheControl
import io.ktor.response.respond
import io.ktor.response.respondTextWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.kodein.di.instance
import org.kodein.di.ktor.di
import org.kodein.di.on
import kotlin.coroutines.coroutineContext

/**
 * This is used by the generated server application to dispatch update notifications as a
 * server sent events stream. It will
 * 1) Get the UpdateNotificationManager using the DI
 * 2) Send any pending notifications that the client has not yet received
 * 3) Will keep the request open adn use the UpdateNotificationManager to listen for updates for
 *    the deviceId and send them as soon as they are received.
 */
suspend fun ApplicationCall.respondUpdateNotifications(repo: DoorDatabaseSyncRepository) {
    response.cacheControl(io.ktor.http.CacheControl.NoCache(null))
    val deviceId = request.queryParameters["deviceId"]?.toInt() ?: 0
    val channel = Channel<DoorServerSentEvent>(capacity = Channel.UNLIMITED)

    val logPrefix = "[respondUpdateNotification device $deviceId]"
    Napier.d("$logPrefix connected",  tag = DoorTag.LOG_TAG)

    val updateManager: ServerUpdateNotificationManager by di().on(this).instance()

    val listener = object: UpdateNotificationListener {
        override fun onNewUpdate(notification: UpdateNotification) {
            channel.offer(notification.asDoorServerSentEvent())
        }
    }

    updateManager.addUpdateNotificationListener(deviceId, listener)

    /*
     * Runs the pinger in the current coroutine context. This ensures if this
     * suspend function is canceled, the pinger will also be canceled.
     */
    val pinger = GlobalScope.launch(coroutineContext) {
        while(true) {
            channel.offer(DoorServerSentEvent("0", "PING", "PING"))
            delay(60000)
        }
    }


    repo.syncHelperEntitiesDao.findPendingUpdateNotifications(deviceId).forEach {
        channel.offer(it.asDoorServerSentEvent())
    }

    try {
        respondTextWriter(contentType = io.ktor.http.ContentType.Text.EventStream) {
            Napier.d("$logPrefix say HELO", tag = DoorTag.LOG_TAG)
            write("id: 0\nevent: HELO\n\n")
            flush()
            for(notification in channel) {
                write("id: ${notification.id}\n")
                write("event: ${notification.event}\n")
                write("data: ${notification.data}\n\n")
                flush()
                Napier.d("$logPrefix:Sent event ${notification.id} for data: ${notification.data}",
                        tag = DoorTag.LOG_TAG)
            }
        }
    } finally {
        Napier.d("respondUpdateNotifications done: close", tag = DoorTag.LOG_TAG)
        pinger.cancelAndJoin()
        updateManager.removeUpdateNotificationListener(deviceId, listener)
        channel.close()
    }
}

/**
 * Server endpoint to receive an acknowledgement from the client that it has received an
 * UpdateNotification. This is done without using the primary key UID because if this was pushed
 * live to the client using ServerUpdateNotificationManager.onNewUpdateNotifications then the
 * key was not known at the time.
 *
 * This will delete the UpdateNotification entity from the database
 * @param repo The DoorDatabaseSyncRepository we are responding for
 */
suspend fun ApplicationCall.respondUpdateNotificationReceived(repo: DoorDatabaseSyncRepository) {
    val deviceId = request.queryParameters["deviceId"]?.toLong() ?: 0
    val tableId = request.queryParameters["tableId"]?.toInt() ?: 0
    val lastModTimestamp = request.queryParameters["lastModTimestamp"]?.toLong() ?: 0

    repo.syncHelperEntitiesDao.deleteUpdateNotification(deviceId, tableId, lastModTimestamp)
    Napier.d("[respondUpdateNotificationReceived] - delete notification for $deviceId / " +
            "table id $tableId ts=$lastModTimestamp")
    respond(HttpStatusCode.NoContent, "")
}
