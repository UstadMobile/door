package com.ustadmobile.door.ktor

import com.ustadmobile.door.DoorConstants.HEADER_NODE
import com.ustadmobile.door.util.NodeIdAuthCache
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import org.kodein.di.on

/**
 * Add an interceptor to check that the node connecting is either a known node (using its known auth) or a new node.
 *
 * This requires NodeIdAuthCache to be available through the DI
 */
fun Route.addNodeIdAndAuthCheckInterceptor(){

    intercept(ApplicationCallPipeline.Features) {
        val header = context.request.header(HEADER_NODE) ?: context.request.queryParameters[HEADER_NODE]
        if(header == null) {
            context.request.call.respond(
                HttpStatusCode.Unauthorized, "Door Node Id and Auth header required, but not provided")
            return@intercept finish()
        }

        try {
            val di: DI = call.closestDI()
            val nodeIdAuthCache: NodeIdAuthCache = di.direct.on(call).instance()

            val (nodeIdStr, nodeAuth) = header.split('/', limit = 2)
            if(!nodeIdAuthCache.verify(nodeIdStr.toInt(), nodeAuth)) {
                context.request.call.respond(
                    HttpStatusCode.Unauthorized, "Invalid nodeId / nodeauth combo")
                return@intercept finish()
            }
        }catch(e: Exception) {
            context.request.call.respond(
                HttpStatusCode.BadRequest, "Exception parsing node id / auth header")
            return@intercept finish()
        }
    }
}
