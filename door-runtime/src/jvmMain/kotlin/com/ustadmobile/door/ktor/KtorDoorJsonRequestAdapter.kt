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

    override fun requireIntParam(paramName: String): Int {
        return call.request.queryParameters[paramName]?.toInt()
            ?: throw IllegalStateException("requireIntParam $paramName not found")
    }

    override fun requireLongParam(paramName: String): Long {
        return call.request.queryParameters[paramName]?.toLong()
            ?: throw IllegalStateException("requireLongParam: $paramName not found")
    }

    override fun requireStringParam(paramName: String): String {
        return call.request.queryParameters[paramName]
            ?: throw IllegalStateException("requireStringParam: $paramName not found")
    }

    override suspend fun requireBodyAsString(): String {
        return call.receiveText()
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
