package com.ustadmobile.door.httpsql

import com.ustadmobile.door.ext.bodyAsJsonObject
import com.ustadmobile.door.jdbc.*
import com.ustadmobile.door.jdbc.types.BigDecimal
import com.ustadmobile.door.jdbc.types.Date
import com.ustadmobile.door.jdbc.types.Time
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class HttpSqlPreparedStatement(
    private val connection: HttpSqlConnection,
    private val preparedStatementId: Int,
    private val generatedKeys: Int,
) : HttpSqlStatement(connection), PreparedStatement, AsyncCloseable {

    private val paramValues: MutableMap<Int, PreparedStatementParam> = mutableMapOf()

    private var closed = false

    private var timeout: Int = 30

    private var generatedKeysResults: ResultSet? = null

    override fun setBoolean(index: Int, value: Boolean) {
        paramValues[index] = PreparedStatementParam(index, listOf(value.toString()), TypesKmp.BOOLEAN)
    }

    override fun setByte(index: Int, value: Byte) {
        paramValues[index] = PreparedStatementParam(index, listOf(value.toString()), TypesKmp.TINYINT)
    }

    override fun setShort(index: Int, value: Short) {
        paramValues[index] = PreparedStatementParam(index, listOf(value.toString()), TypesKmp.SMALLINT)
    }

    override fun setInt(index: Int, value: Int) {
        paramValues[index] = PreparedStatementParam(index, listOf(value.toString()), TypesKmp.INTEGER)
    }

    override fun setLong(index: Int, value: Long) {
        paramValues[index] = PreparedStatementParam(index, listOf(value.toString()), TypesKmp.BIGINT)
    }

    override fun setFloat(index: Int, value: Float) {
        paramValues[index] = PreparedStatementParam(index, listOf(value.toString()), TypesKmp.FLOAT)
    }

    override fun setDouble(index: Int, value: Double) {
        paramValues[index] = PreparedStatementParam(index, listOf(value.toString()), TypesKmp.DOUBLE)
    }

    override fun setBigDecimal(index: Int, value: BigDecimal) {
        throw SQLException("Not supported")
    }

    override fun setString(index: Int, value: String?) {
        paramValues[index] = PreparedStatementParam(index, listOf(value), TypesKmp.LONGVARCHAR)
    }

    override fun setBytes(index: Int, value: ByteArray) {
        throw SQLException("Not supported")
    }

    override fun setDate(index: Int, value: Date) {
        throw SQLException("Not supported")
    }

    override fun setTime(index: Int, value: Time) {
        throw SQLException("Not supported")
    }

    override fun setObject(index: Int, value: Any?) {
        throw SQLException("Not supported")
    }

    override fun setArray(index: Int, array: Array) {
        TODO("setArray: Not yet implemented")
    }

    override fun executeUpdate(): Int {
        throw SQLException("Not supported")
    }

    override suspend fun executeUpdateAsync(): Int {
        val result = connection.httpClient.post("${connection.endpointUrl}/connection" +
                "/${connection.httpSqlConnectionInfo.connectionId}/preparedStatement/${preparedStatementId}/update"
        ) {
            setBody(PreparedStatementExecRequest(paramValues.values.toList()))
            contentType(ContentType.Application.Json)
        }.bodyAsJsonObject(connection.json)

        generatedKeysResults = if(generatedKeys == StatementConstantsKmp.RETURN_GENERATED_KEYS) {
            HttpSqlResultSet(result[HttpSqlPaths.KEY_EXEC_UPDATE_GENERATED_KEYS]?.jsonObject
                ?: throw IllegalArgumentException("executeUpdateAsync: No ResultSet even though return generated keys set"))
        } else {
            null
        }

        return result[HttpSqlPaths.KEY_EXEC_UPDATE_NUM_ROWS_CHANGED]?.jsonPrimitive?.int ?: 0
    }

    override suspend fun executeQueryAsyncInt(): ResultSet {
        val jsonResultObject = connection.httpClient.post("${connection.endpointUrl}/connection" +
                "/${connection.httpSqlConnectionInfo.connectionId}/preparedStatement/${preparedStatementId}/query"
        ) {
            setBody(PreparedStatementExecRequest(paramValues.values.toList()))
            contentType(ContentType.Application.Json)
        }.bodyAsJsonObject(connection.json)
        return HttpSqlResultSet(jsonResultObject)
    }

    override fun executeQuery(): ResultSet {
        throw SQLException("executeQuery synchronous NOT SUPPORTED by HttpSql")
    }

    override fun setNull(parameterIndex: Int, sqlType: Int) {
        paramValues[parameterIndex] = PreparedStatementParam(parameterIndex, listOf(null), sqlType)
    }

    override fun close() {
        throw SQLException("Synchronous close not supported on HttpSqlPreparedStatement")
    }

    override suspend fun closeAsync() {
        connection.httpClient.get("${connection.endpointUrl}/connection" +
                "/${connection.httpSqlConnectionInfo.connectionId}/preparedStatement/${preparedStatementId}/close"
        ) {

        }.discardRemaining()
    }

    override fun isClosed() = closed

    override fun getConnection() = connection

    override fun getGeneratedKeys(): ResultSet {
        return generatedKeysResults ?: throw SQLException("No generated keys")
    }

    override fun setQueryTimeout(seconds: Int) {
        timeout = seconds
    }
}