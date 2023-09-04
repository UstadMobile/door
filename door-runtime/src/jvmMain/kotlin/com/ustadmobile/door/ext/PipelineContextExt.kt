package com.ustadmobile.door.ext

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.DoorDatabaseWrapper
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.pipeline.*
import org.kodein.di.direct
import org.kodein.di.ktor.closestDI
import org.kodein.di.on
import org.kodein.type.TypeToken

/**
 * Gets the database (unwrapped it is using DoorDatabaseSyncableReadOnlyWrapper) for the current
 * application call pipeline context.
 *
 * This is used by generated code in Ktor routes.
 */
@Suppress("UNCHECKED_CAST")
fun <T: RoomDatabase> PipelineContext<Unit, ApplicationCall>.unwrappedDbOnCall(typeToken: TypeToken<T>, tag: Int = DoorTag.TAG_DB): T{
    val db = closestDI().on(call).direct.Instance(typeToken, tag = tag)
    return if(db is DoorDatabaseWrapper<*>) {
        db.realDatabase as T
    }else {
        db
    }
}

/**
 * Convenience function to get the remote node id (e.g. the node that is making the http
 * request) and the node auth. Throws an exception if this does not exist
 */
fun PipelineContext<Unit, ApplicationCall>.requireRemoteNodeIdAndAuth(): Pair<Long, String> {
    val header = context.request.header(DoorConstants.HEADER_NODE_AND_AUTH)
        ?: context.request.queryParameters[DoorConstants.HEADER_NODE_AND_AUTH]
        ?: throw IllegalStateException("remoteNodeIdAndAuth: no id and auth header or query param provided")
    val (nodeIdStr, nodeAuth) = header.split('/', limit = 2)
    return Pair(nodeIdStr.toLong(), nodeAuth)
}

