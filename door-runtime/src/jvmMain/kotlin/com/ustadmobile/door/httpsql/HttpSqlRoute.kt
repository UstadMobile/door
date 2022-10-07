package com.ustadmobile.door.httpsql

import com.ustadmobile.door.DoorRootDatabase
import com.ustadmobile.door.ext.rootDatabase
import com.ustadmobile.door.httpsql.HttpSqlPaths.KEY_EXEC_UPDATE_NUM_ROWS_CHANGED
import com.ustadmobile.door.httpsql.HttpSqlPaths.KEY_EXEC_UPDATE_GENERATED_KEYS
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.room.RoomDatabase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import com.ustadmobile.door.httpsql.HttpSqlPaths.KEY_RESULT_ROWS
import com.ustadmobile.door.httpsql.HttpSqlPaths.PARAM_AUTOCOMMIT
import com.ustadmobile.door.httpsql.HttpSqlPaths.PARAM_CONNECTION_ID
import com.ustadmobile.door.httpsql.HttpSqlPaths.PARAM_PREPAREDSTATEMENT_ID
import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.jdbc.StatementConstantsKmp.RETURN_GENERATED_KEYS
import com.ustadmobile.door.jdbc.ext.toHttpSqlResultSetJsonObject
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
        val generatedKeys: Int,
        val preparedStatementId: Int,
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

    fun ApplicationCall.preparedStatementGeneratedKeys(): Int {
        return preparedStatementHandles[(parameters[PARAM_PREPAREDSTATEMENT_ID]?.toInt() ?: 0)]?.generatedKeys ?: -1
    }

    get("/") {
        call.respondText("Door HttpSQL endpoint")
    }

    getWithAuthCheck("connection/open") {
        val connectionId = connectionIdAtomic.incrementAndGet()
        val db = databaseProvider.databaseForCall(call)
        val connection = (db.rootDatabase as DoorRootDatabase).dataSource.connection
        connectionHandles[connectionId] = ConnectionHandle(connection, connectionId)
        call.respond(HttpSqlConnectionInfo(connectionId))
    }

    getWithAuthCheck("connection/{connectionId}/close") {
        call.connection()?.apply {
            close()
        }
        connectionHandles.remove(call.request.queryParameters[PARAM_CONNECTION_ID]?.toInt() ?: 0)
        call.respond(HttpStatusCode.NoContent, "")
    }

    postWithAuthCheck("connection/{connectionId}/statement/query") {
        val querySql = call.receiveText()
        val resultJsonArray = call.requireConnection().createStatement().use { stmt ->
            stmt.executeQuery(querySql).use { result ->
                result.toJsonArray()
            }
        }

        val resultObject = buildJsonObject {
            put(KEY_RESULT_ROWS, resultJsonArray)
        }

        call.respondText(contentType = ContentType.Application.Json,
            text = json.encodeToString(JsonObject.serializer(), resultObject))
    }

    postWithAuthCheck("connection/{connectionId}/statement/update") {
        val querySql = call.receiveText()


        val numUpdates = call.requireConnection().createStatement().use {
            it.executeUpdate(querySql)
        }
        val updateJsonResponse = buildJsonObject {
            put(KEY_EXEC_UPDATE_NUM_ROWS_CHANGED, JsonPrimitive(numUpdates))
        }

        call.respondText(contentType = ContentType.Application.Json,
            text = json.encodeToString(JsonObject.serializer(), updateJsonResponse))
    }

    postWithAuthCheck("connection/{connectionId}/preparedStatement/create") {
        val connection = call.requireConnection()
        val preparedStatementRequest : PrepareStatementRequest = call.receive()
        val preparedStatement = connection.prepareStatement(preparedStatementRequest.sql,
            preparedStatementRequest.generatedKeys)
        val preparedStatementId = preparedStatementIdAtomic.incrementAndGet()
        preparedStatementHandles[preparedStatementId] = PreparedStatementHandle(preparedStatement,
            preparedStatementRequest.generatedKeys, preparedStatementId)
        call.respond(PrepareStatementResponse(preparedStatementId, call.parameters[PARAM_CONNECTION_ID]?.toInt()?: -1))
    }

    getWithAuthCheck("connection/{connectionId}/preparedStatement/{preparedStatementId}/close") {
        val preparedStatementId = call.request.queryParameters[PARAM_PREPAREDSTATEMENT_ID]?.toInt() ?: 0
        call.preparedStatement()?.also {
            it.close()
            preparedStatementHandles.remove(preparedStatementId)
        }
        call.respond(HttpStatusCode.NoContent, "")
    }

    getWithAuthCheck("connection/{connectionId}/setAutoCommit") {
        val connection = call.requireConnection()
        val commit = call.request.queryParameters[PARAM_AUTOCOMMIT]?.toBoolean() ?: false
        connection.autoCommit = commit
        call.respond(HttpStatusCode.NoContent, "")
    }

    getWithAuthCheck("connection/{connectionId}/commit") {
        val connection = call.requireConnection()
        connection.commit()
        call.respond(HttpStatusCode.NoContent, "")
    }



    postWithAuthCheck("connection/{connectionId}/preparedStatement/{preparedStatementId}/query") {
        val preparedStatement = call.requirePreparedStatement()
        val execRequest: PreparedStatementExecRequest = call.receive()
        execRequest.params.forEach {
            preparedStatement.setPreparedStatementParam(it)
        }

        val results = preparedStatement.executeQuery().use { it.toHttpSqlResultSetJsonObject() }

        call.respondText(contentType = ContentType.Application.Json, text = json.encodeToString(JsonObject.serializer(),
            results))
    }

    postWithAuthCheck("connection/{connectionId}/preparedStatement/{preparedStatementId}/update") {
        val preparedStatement = call.requirePreparedStatement()
        val execRequest: PreparedStatementExecRequest = call.receive()
        execRequest.params.forEach {
            preparedStatement.setPreparedStatementParam(it)
        }

        val numUpdates = preparedStatement.executeUpdate()

        val updateJsonResponse = buildJsonObject {
            put(KEY_EXEC_UPDATE_NUM_ROWS_CHANGED, JsonPrimitive(numUpdates))

            if(call.preparedStatementGeneratedKeys() == RETURN_GENERATED_KEYS) {
                put(KEY_EXEC_UPDATE_GENERATED_KEYS,
                    preparedStatement.generatedKeys.use { it.toHttpSqlResultSetJsonObject() })
            }
        }

        call.respondText(contentType = ContentType.Application.Json,
            text = json.encodeToString(JsonObject.serializer(), updateJsonResponse))
    }



    //Server sent events - invalidations
}