package com.ustadmobile.door.ktor

import com.ustadmobile.door.ext.requireRemoteNodeIdAndAuth
import com.ustadmobile.door.util.NodeIdAuthCache
import io.ktor.application.*
import io.ktor.http.*
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
        try {
            val di: DI = call.closestDI()
            val nodeIdAuthCache: NodeIdAuthCache = di.direct.on(call).instance()
            val remoteNodeIdAndAuth = requireRemoteNodeIdAndAuth()

            if(!nodeIdAuthCache.verify(remoteNodeIdAndAuth.first, remoteNodeIdAndAuth.second)) {
                context.request.call.respond(HttpStatusCode.Unauthorized, "Invalid nodeId / nodeauth combo")
                return@intercept finish()
            }
        }catch(e: Exception) {
            context.request.call.respond(HttpStatusCode.BadRequest, "Exception parsing node id / auth header")
            return@intercept finish()
        }
    }
}
