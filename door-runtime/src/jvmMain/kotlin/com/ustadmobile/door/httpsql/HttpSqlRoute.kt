package com.ustadmobile.door.httpsql

import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.ext.rootDatabase
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.room.RoomDatabase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.server.request.receiveText
import com.ustadmobile.door.ext.rowsToJsonArray
import com.ustadmobile.door.jdbc.ext.columnTypeMap
import kotlinx.serialization.json.*


fun Route.HttpSql(
    db: RoomDatabase,
    authChecker: (ApplicationCall) -> Boolean,
    json: Json,
) {


    class ConnectionHandle(
        val connection: Connection,
        val connectionId: Int
    )

    val connectionIdAtomic = AtomicInteger()

    val connectionHandles = mutableMapOf<Int, ConnectionHandle>()

    fun Route.routeWithAuthCheck(
        path: String,
        httpMethod: HttpMethod,
        body: PipelineInterceptor<Unit, ApplicationCall>
    ) : Route {
        return route(path, httpMethod) {
            handle {
                if(authChecker(call)) {
                    body.invoke(this, Unit)
                }else {
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }
        }
    }

    fun Route.getWithAuthCheck(
        path: String,
        body: PipelineInterceptor<Unit, ApplicationCall>
    ) = routeWithAuthCheck(path, HttpMethod.Get, body)

    fun Route.postWithAuthCheck(
        path: String,
        body: PipelineInterceptor<Unit, ApplicationCall>
    ) = routeWithAuthCheck(path, HttpMethod.Post, body)

    fun ApplicationCall.connection(): Connection? {
        val connectionId = request.queryParameters["connectionId"]?.toInt() ?: 0
        return connectionHandles[connectionId]?.connection
    }

    fun ApplicationCall.requireConnection(): Connection {
        return connection() ?: throw IllegalStateException("No connection for call")
    }

    getWithAuthCheck("open") {
        val connectionId = connectionIdAtomic.incrementAndGet()
        val connection = (db.rootDatabase as DoorDatabaseJdbc).dataSource.connection
        connectionHandles[connectionId] = ConnectionHandle(connection, connectionId)
        call.respond(HttpSqlConnectionInfo(connectionId))
    }

    getWithAuthCheck("close") {
        call.connection()?.apply {
            commit()
            close()
        }
        connectionHandles.remove(call.request.queryParameters["connectionId"]?.toInt() ?: 0)
    }

    postWithAuthCheck("statementQuery") {
        val querySql = call.receiveText()
        val resultJsonArray = call.requireConnection().createStatement().use { stmt ->
            stmt.executeQuery(querySql).use { result ->
                result.rowsToJsonArray(result.columnTypeMap())
            }
        }

        val resultObject = buildJsonObject {
            put("rows", resultJsonArray)
        }

        call.respondText(contentType = ContentType.Application.Json,
            text = json.encodeToString(JsonObject.serializer(), resultObject))
    }

    postWithAuthCheck("statementUpdate") {
        val querySql = call.receiveText()
        val numUpdates = call.requireConnection().createStatement().use {
            it.executeUpdate(querySql)
        }

        val resultObject = buildJsonObject {
            put("updates", JsonPrimitive(numUpdates))
        }

        call.respondText(contentType =  ContentType.Application.Json,
            text = json.encodeToString(JsonObject.serializer(), resultObject))
    }



    //Server sent events - invalidations
}