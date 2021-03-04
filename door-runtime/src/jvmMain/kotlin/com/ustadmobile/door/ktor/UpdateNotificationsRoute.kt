package com.ustadmobile.door.ktor

import com.ustadmobile.door.ClientSyncManager.Companion.API_UPDATE_NOTIFICATION_ROUTE
import com.ustadmobile.door.ClientSyncManager.Companion.SUFFIX_UPDATE_ACK
import com.ustadmobile.door.ClientSyncManager.Companion.SUFFIX_UPDATE_EVENTS
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseSyncRepository
import com.ustadmobile.door.ext.DoorTag
import io.ktor.application.*
import io.ktor.routing.*
import org.kodein.di.Instance
import org.kodein.di.ktor.di
import org.kodein.di.on
import org.kodein.type.TypeToken

/**
 * KTOR Route that provides two routes to handle update notifications:
 *
 * 1) A route that provides a Server Sent Events (SSE) source to "send" update notifications to a client
 * 2) A route that clients use to acknowledge the events that they have received and processed
 *
 * This is used by the Route which is generated for any syncable database.
 */
fun <T : DoorDatabase> Route.UpdateNotificationsRoute(typeToken: TypeToken<T>) {
    route(API_UPDATE_NOTIFICATION_ROUTE) {
        get(SUFFIX_UPDATE_EVENTS) {
            val repo: T by di().on(call).Instance(typeToken, tag = DoorTag.TAG_REPO)
            call.respondUpdateNotifications(repo as DoorDatabaseSyncRepository)
        }

        get(SUFFIX_UPDATE_ACK) {
            val repo: T by di().on(call).Instance(typeToken, tag = DoorTag.TAG_REPO)
            call.respondUpdateNotificationReceived(repo as DoorDatabaseSyncRepository)
        }
    }
}
