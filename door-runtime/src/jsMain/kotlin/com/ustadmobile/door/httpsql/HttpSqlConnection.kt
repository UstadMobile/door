package com.ustadmobile.door.httpsql

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DatabaseMetadata
import com.ustadmobile.door.jdbc.PreparedStatement
import io.ktor.client.*
import kotlinx.serialization.json.Json


class HttpSqlConnection(
    /**
     * The endpoint url, which MUST end with a /
     */
    internal val endpointUrl: String,
    internal val httpSqlConnectionInfo: HttpSqlConnectionInfo,
    internal val httpClient: HttpClient,
    internal val json: Json,
): Connection{

    private var closed = false

    override fun setAutoCommit(commit: Boolean) {

    }

    override fun prepareStatement(sql: String): PreparedStatement {
        TODO("Not yet implemented")
    }

    override fun prepareStatement(sql: String, autoGeneratedKeys: Int): PreparedStatement {
        TODO("Not yet implemented")
    }

    override fun createStatement() = HttpSqlStatement(this)

    override fun commit() {
        TODO("Not yet implemented")
    }

    override fun rollback() {
        TODO("Not yet implemented")
    }

    override fun close() {
        closed = true
    }

    override fun isClosed() = closed

    override fun createArrayOf(arrayType: String, objects: Array<out Any?>): com.ustadmobile.door.jdbc.Array {
        TODO("Not yet implemented")
    }

    override fun getMetaData(): DatabaseMetadata {
        TODO("Not yet implemented")
    }

    override fun getAutoCommit(): Boolean {
        TODO("Not yet implemented")
    }
}