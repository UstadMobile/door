package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.JdbcArrayProxy
import com.ustadmobile.door.PreparedStatementArrayProxy
import com.ustadmobile.door.PreparedStatementConfig
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.PreparedStatement

/**
 * Create a PreparedStatement according to a given PreparedStatementConfig and jdbcDbType
 */
fun Connection.prepareStatement(
    stmtConfig: PreparedStatementConfig,
    jdbcDbType: Int
) : PreparedStatement {
    val pgSql = stmtConfig.postgreSql
    val sqlToUse = if(pgSql != null && jdbcDbType == DoorDbType.POSTGRES ) {
        pgSql
    }else {
        stmtConfig.sql
    }

    return when {
        !stmtConfig.hasListParams -> prepareStatement(sqlToUse, stmtConfig.generatedKeys)
        (jdbcDbType== DoorDbType.POSTGRES) -> prepareStatement(sqlToUse.adjustQueryWithSelectInParam(jdbcDbType))
        else -> PreparedStatementArrayProxy(sqlToUse, this)
    }
}

/**
 * Shorthand to get the dbType using the metadata product name
 */
fun Connection.dbType() = DoorDbType.typeIntFromProductName(getMetaData().getDatabaseProductName())


/**
 * Create an JDBC array. If the dbType supports arrays natively, the native support will be used. Otherwise this will
 * fallback to using the JdbcArrayProxy (e.g. for use on SQLite)
 */
fun Connection.createArrayOrProxyArrayOf(
    arrayType: String,
    objects: kotlin.Array<out Any?>
): com.ustadmobile.door.jdbc.Array  {
    return if(dbType() == DoorDbType.POSTGRES) {
        createArrayOf(arrayType, objects)
    }else {
        JdbcArrayProxy(arrayType, objects)
    }
}
