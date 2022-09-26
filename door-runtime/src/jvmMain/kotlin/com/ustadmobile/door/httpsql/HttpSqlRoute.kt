package com.ustadmobile.door.httpsql

import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.room.RoomDatabase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

data class HttpSqlConnectionInfo(val connectionId: Int){

}

fun Route.HttpSql(
    db: RoomDatabase,
    authChecker: (ApplicationCall) -> Boolean
) {


    class ConnectionHandle(
        val connection: Connection,
        val connectionId: Int
    )

    val connectionIdAtomic = AtomicInteger()

    val connectionHandles = mutableMapOf<Int, ConnectionHandle>()

    fun Route.getWithAuthCheck(
        path: String,
        body: PipelineInterceptor<Unit, ApplicationCall>
    ) = get(path) {
        if(authChecker(call)) {
            body.invoke(this, Unit)
        }else {
            call.respond(HttpStatusCode.Unauthorized)
        }
    }

    getWithAuthCheck("open") {
        val connectionId = connectionIdAtomic.incrementAndGet()
        val connection = (db as DoorDatabaseJdbc).dataSource.connection
        connectionHandles[connectionId] = ConnectionHandle(connection, connectionId)
        call.respond(HttpSqlConnectionInfo(connectionId))
    }

    getWithAuthCheck("close") {
        val connectionId = call.request.queryParameters["connectionId"]?.toInt() ?: 0
        connectionHandles[connectionId]?.connection?.apply {
            commit()
            close()
        }
    }

    get("query") {

    }

    get("update") {

    }

    //Server sent events - invalidations
}