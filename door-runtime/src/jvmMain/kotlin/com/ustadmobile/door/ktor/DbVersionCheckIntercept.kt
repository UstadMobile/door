package com.ustadmobile.door.ktor

import com.ustadmobile.door.DoorConstants.HEADER_DBVERSION
import com.ustadmobile.door.ext.DoorTag
import io.github.aakira.napier.Napier
import io.ktor.application.ApplicationCallPipeline
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route


/**
 * Extension function that adds a database version check to the route. This is used by the
 * generated ktor server application when @MinSyncVersion annotation is added.
 */
fun Route.addDbVersionCheckIntercept(minVersion: Int) {
    intercept(ApplicationCallPipeline.Features) {
        val clientVersion: Int = this.context.request.headers[HEADER_DBVERSION]?.toInt() ?:
            this.context.request.queryParameters[HEADER_DBVERSION]?.toInt() ?: 0
        if(clientVersion < minVersion) {
            Napier.w("Bad Request: client did not meet minimum version required" +
                    "Required: $minVersion, client version = $clientVersion",
                    tag = DoorTag.LOG_TAG)
            context.request.call.respond(HttpStatusCode.BadRequest,
                    "Door DB Version does not meet minimum required: $minVersion")
            return@intercept finish()
        }
    }
}