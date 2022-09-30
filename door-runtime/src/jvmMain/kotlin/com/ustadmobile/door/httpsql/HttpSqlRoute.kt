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
import com.ustadmobile.door.ext.rowsToJsonArray
import com.ustadmobile.door.httpsql.HttpSqlPaths.KEY_ROWS
import com.ustadmobile.door.httpsql.HttpSqlPaths.PARAM_CONNECTION_ID
import com.ustadmobile.door.httpsql.HttpSqlPaths.PARAM_PREPAREDSTATEMENT_ID
import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.jdbc.ext.columnTypeMap
import com.ustadmobile.door.ktor.DatabaseProvider
import io.ktor.server.request.*
import kotlinx.serialization.json.*
import com.ustadmobile.door.jdbc.ext.toJsonArray


fun Route.HttpSql(
    databaseProvider: DatabaseProvider<RoomDatabase>,
    authChecker: (ApplicationCall) -> Boolean,
    json: Json,
) {


    class ConnectionHandle(
        val connection: Connection,
        val connectionId: Int
    )

    class PreparedStatementHandle(
        val preparedStatement: PreparedStatement,
        val preparedStatementId: Int
    )

    val connectionIdAtomic = AtomicInteger()

    val preparedStatementIdAtomic = AtomicInteger()

    val connectionHandles = mutableMapOf<Int, ConnectionHandle>()

    val preparedStatementHandles = mutableMapOf<Int, PreparedStatementHandle>()

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
        val connectionId = parameters["connectionId"]?.toInt() ?: 0
        return connectionHandles[connectionId]?.connection
    }

    fun ApplicationCall.requireConnection(): Connection {
        return connection() ?: throw IllegalStateException("No connection for call")
    }

    fun ApplicationCall.preparedStatement(): PreparedStatement? {
        return preparedStatementHandles[(parameters[PARAM_PREPAREDSTATEMENT_ID]?.toInt() ?: 0)]?.preparedStatement
    }

    fun ApplicationCall.requirePreparedStatement(): PreparedStatement {
        return preparedStatement() ?: throw SQLException("Could not find preparedStatement")
    }

    get("/") {
        call.respondText("Door HttpSQL endpoint")
    }

    getWithAuthCheck("connection/open") {
        val connectionId = connectionIdAtomic.incrementAndGet()
        val db = databaseProvider.databaseForCall(call)
        val connection = (db.rootDatabase as DoorDatabaseJdbc).dataSource.connection
        connectionHandles[connectionId] = ConnectionHandle(connection, connectionId)
        call.respond(HttpSqlConnectionInfo(connectionId))
    }

    getWithAuthCheck("connection/{connectionId}/close") {
        call.connection()?.apply {
            commit()
            close()
        }
        connectionHandles.remove(call.request.queryParameters[PARAM_CONNECTION_ID]?.toInt() ?: 0)
    }

    postWithAuthCheck("connection/{connectionId}/statement/query") {
        val querySql = call.receiveText()
        val resultJsonArray = call.requireConnection().createStatement().use { stmt ->
            stmt.executeQuery(querySql).use { result ->
                result.toJsonArray()
            }
        }

        val resultObject = buildJsonObject {
            put(KEY_ROWS, resultJsonArray)
        }

        call.respondText(contentType = ContentType.Application.Json,
            text = json.encodeToString(JsonObject.serializer(), resultObject))
    }

    postWithAuthCheck("connection/{connectionId}/statement/update") {
        val querySql = call.receiveText()
        val numUpdates = call.requireConnection().createStatement().use {
            it.executeUpdate(querySql)
        }
        call.respond(HttpSqlUpdateResult(numUpdates))
    }

    postWithAuthCheck("connection/{connectionId}/preparedStatement/create") {
        val connection = call.requireConnection()
        val preparedStatementRequest : PrepareStatementRequest = call.receive()
        val preparedStatement = connection.prepareStatement(preparedStatementRequest.sql,
            preparedStatementRequest.generatedKeys)
        val preparedStatementId = preparedStatementIdAtomic.incrementAndGet()
        preparedStatementHandles[preparedStatementId] = PreparedStatementHandle(preparedStatement, preparedStatementId)
        call.respond(PrepareStatementResponse(preparedStatementId, call.parameters[PARAM_CONNECTION_ID]?.toInt()?: -1))
    }

    getWithAuthCheck("connection/{connectionId}/preparedStatement/{preparedStatementId}/close") {
        val preparedStatementId = call.request.queryParameters[PARAM_PREPAREDSTATEMENT_ID]?.toInt() ?: 0
        call.preparedStatement()?.also {
            it.close()
            preparedStatementHandles.remove(preparedStatementId)
        }
    }

    postWithAuthCheck("connection/{connectionId}/preparedStatement/{preparedStatementId}/query") {
        val preparedStatement = call.requirePreparedStatement()
        val execRequest: PreparedStatementExecRequest = call.receive()
        execRequest.params.forEach {
            preparedStatement.setPreparedStatementParam(it)
        }

        val results = preparedStatement.executeQuery().use { it.toJsonArray() }
        val resultJsonObject = buildJsonObject {
            put(KEY_ROWS, results)
        }

        call.respondText(contentType = ContentType.Application.Json, text = json.encodeToString(JsonObject.serializer(),
            resultJsonObject))
    }

    postWithAuthCheck("connection/{connectionId}/preparedStatement/{preparedStatementId}/update") {
        val preparedStatement = call.requirePreparedStatement()
        val execRequest: PreparedStatementExecRequest = call.receive()
        execRequest.params.forEach {
            preparedStatement.setPreparedStatementParam(it)
        }

        val numUpdates = preparedStatement.executeUpdate()
        call.respond(HttpSqlUpdateResult(numUpdates))
    }



    //Server sent events - invalidations
}