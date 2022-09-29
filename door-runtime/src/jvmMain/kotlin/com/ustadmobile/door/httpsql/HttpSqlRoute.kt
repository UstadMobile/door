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
import com.ustadmobile.door.httpsql.HttpSqlPaths.KEY_UPDATES
import com.ustadmobile.door.httpsql.HttpSqlPaths.PARAM_CONNECTION_ID
import com.ustadmobile.door.httpsql.HttpSqlPaths.PARAM_PREPAREDSTATEMENT_ID
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_CONNECTION_CLOSE
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_CONNECTION_OPEN
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_PREPARED_STATEMENT_QUERY
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_PREPARED_STATEMENT_UPDATE
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_PREPARE_STATEMENT
import com.ustadmobile.door.httpsql.HttpSqlPaths.PATH_STATEMENT_QUERY
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
        val connectionId = request.queryParameters["connectionId"]?.toInt() ?: 0
        return connectionHandles[connectionId]?.connection
    }

    fun ApplicationCall.requireConnection(): Connection {
        return connection() ?: throw IllegalStateException("No connection for call")
    }


    fun ApplicationCall.requirePreparedStatement(): PreparedStatement {
        return preparedStatementHandles[(request.queryParameters[PARAM_PREPAREDSTATEMENT_ID]?.toInt() ?: 0)]?.preparedStatement
            ?: throw SQLException("Could not find preparedStatement")
    }

    get("/") {
        call.respondText("Door HttpSQL endpoint")
    }

    getWithAuthCheck(PATH_CONNECTION_OPEN) {
        val connectionId = connectionIdAtomic.incrementAndGet()
        val db = databaseProvider.databaseForCall(call)
        val connection = (db.rootDatabase as DoorDatabaseJdbc).dataSource.connection
        connectionHandles[connectionId] = ConnectionHandle(connection, connectionId)
        call.respond(HttpSqlConnectionInfo(connectionId))
    }

    getWithAuthCheck(PATH_CONNECTION_CLOSE) {
        call.connection()?.apply {
            commit()
            close()
        }
        connectionHandles.remove(call.request.queryParameters[PARAM_CONNECTION_ID]?.toInt() ?: 0)
    }

    postWithAuthCheck(PATH_STATEMENT_QUERY) {
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

    postWithAuthCheck("statementUpdate") {
        val querySql = call.receiveText()
        val numUpdates = call.requireConnection().createStatement().use {
            it.executeUpdate(querySql)
        }

        val resultObject = buildJsonObject {
            put(KEY_UPDATES, JsonPrimitive(numUpdates))
        }

        call.respondText(contentType =  ContentType.Application.Json,
            text = json.encodeToString(JsonObject.serializer(), resultObject))
    }

    postWithAuthCheck(PATH_PREPARE_STATEMENT) {
        val connection = call.requireConnection()
        val preparedStatementRequest : PrepareStatementRequest = call.receive()
        val preparedStatement = connection.prepareStatement(preparedStatementRequest.sql,
            preparedStatementRequest.generatedKeys)
        val preparedStatementId = preparedStatementIdAtomic.incrementAndGet()
        preparedStatementHandles[preparedStatementId] = PreparedStatementHandle(preparedStatement, preparedStatementId)
        call.respond(PrepareStatementResponse(preparedStatementId))
    }

    postWithAuthCheck(PATH_PREPARED_STATEMENT_QUERY) {
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

    postWithAuthCheck(PATH_PREPARED_STATEMENT_UPDATE) {
        val preparedStatement = call.requirePreparedStatement()
        val execRequest: PreparedStatementExecRequest = call.receive()
        execRequest.params.forEach {
            preparedStatement.setPreparedStatementParam(it)
        }

        val numUpdates = preparedStatement.executeUpdate()

        val resultObject = buildJsonObject {
            put(KEY_UPDATES, JsonPrimitive(numUpdates))
        }

        call.respondText(contentType =  ContentType.Application.Json,
            text = json.encodeToString(JsonObject.serializer(), resultObject))
    }



    //Server sent events - invalidations
}