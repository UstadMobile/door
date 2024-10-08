package com.ustadmobile.door.ktor

import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.http.DoorJsonRequest
import com.ustadmobile.door.room.RoomDatabase
import io.ktor.server.application.*
import io.ktor.server.request.*

class KtorDoorJsonRequestAdapter(
    private val call: ApplicationCall,
    override val db: RoomDatabase,
) : DoorJsonRequest {

    override fun queryParam(paramName: String): String? {
        return call.request.queryParameters[paramName]
    }

    override fun requireParam(paramName: String): String {
        return call.request.queryParameters[paramName]
            ?: throw IllegalStateException("requireStringParam: $paramName not found")
    }

    override suspend fun requireBodyAsString(): String {
        return call.receiveText()
    }

    @Suppress("RedundantNullableReturnType")
    override suspend fun bodyAsStringOrNull(): String? {
        return requireBodyAsString()
    }

    override fun requireHeader(header: String): String {
        return call.request.header(header) ?: throw IllegalStateException("requireHeader: $header not found")
    }

    override fun requireNodeId(): Long {
        val nodeIdAndAuthHeader = call.request.header(DoorConstants.HEADER_NODE_AND_AUTH)
            ?: throw IllegalStateException("requireNodeId(): header not found")
        return nodeIdAndAuthHeader.substringBefore("/").toLong()
    }
}

fun ApplicationCall.toDoorRequest(db : RoomDatabase): DoorJsonRequest {
    return KtorDoorJsonRequestAdapter(this, db)
}
