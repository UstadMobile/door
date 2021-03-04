package com.ustadmobile.door

import java.sql.*

internal class PreparedStatementResultSetWrapper(private val resultSet: ResultSet, private val stmt: PreparedStatement) : ResultSet by resultSet {

    @Throws(SQLException::class)
    override fun close() {
        try {
            if (!resultSet.isClosed)
                resultSet.close()
        } catch (e: SQLException) {
            throw e
        } finally {
            if (!stmt.connection.isClosed && !stmt.isClosed)
                stmt.close()
        }
    }

}
