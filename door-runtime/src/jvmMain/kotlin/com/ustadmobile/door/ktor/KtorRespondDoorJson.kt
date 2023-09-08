package com.ustadmobile.door.ktor

import com.ustadmobile.door.http.DoorJsonResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.respondDoorJson(response: DoorJsonResponse) {
    response.headers.forEach { header ->
        this.response.header(header.first, header.second)
    }

    respondText(
        text = response.bodyText,
        contentType = ContentType.parse(response.contentType),
        status = HttpStatusCode.fromValue(response.responseCode)
    )
}