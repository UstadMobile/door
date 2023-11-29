package com.ustadmobile.door

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.SQLException

actual class PreparedStatementArrayProxy actual constructor(
    query: String,
    connection: Connection
) : PreparedStatementArrayProxyCommon(query, connection) {

    private var closed = false

    override fun executeUpdate(sql: String): Int {
        return prepareStatement().executeUpdate()
    }

    override suspend fun executeUpdateAsync(): Int {
        return prepareStatement().executeUpdateAsync()
    }

    override suspend fun executeUpdateAsyncJs(sql: String): Int {
        return prepareStatement().executeUpdateAsyncJs(sql)
    }

    override suspend fun executeQueryAsyncInt(): ResultSet {
        return prepareStatement().executeQueryAsyncInt()
    }

    override fun getGeneratedKeys(): ResultSet {
        throw SQLException("PreparedStatementArrayProxy does not support getting generated keys", null)
    }

    override fun close() {
        closed = true
    }

    override fun isClosed() = closed

    override fun getConnection() = connectionInternal
}
