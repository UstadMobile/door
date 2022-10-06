package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.ext.rowsToJsonArray
import com.ustadmobile.door.jdbc.ResultSet
import kotlinx.serialization.json.*
import com.ustadmobile.door.ext.getJsonPrimitive
import com.ustadmobile.door.httpsql.HttpSqlPaths

/**
 * Map of the column name to the Sql Types (e.g. TypesKmp) value for the column type
 */
fun ResultSet.columnTypeMap(): Map<String, Int> {
    val metaDataVal = metaData
    return (1..metaDataVal.columnCount).associate {
        metaDataVal.getColumnLabel(it) to metaDataVal.getColumnType(it)
    }
}

/**
 * Turn this into a JsonObject suitable for HttpSqlResultSet
 *
 * See HttpSqlResultSet on JS
 */
fun ResultSet.toHttpSqlResultSetJsonObject() : JsonObject  {
    val resultMetaData = metaData

    val colNames = (1 .. resultMetaData.columnCount).map { resultMetaData.getColumnLabel(it) }


    val colTypeList: List<Int> = (0 until resultMetaData.columnCount).map { resultMetaData.getColumnType(it + 1) }


    val colNamesJsonArr = JsonArray(colNames.map { JsonPrimitive(it) })

    val rowsJsonArray = buildJsonArray {
        while(next()) {
            addJsonArray {
                colTypeList.forEachIndexed { index, colType ->
                    add(getJsonPrimitive(index + 1, colType))
                }
            }
        }
    }

    return buildJsonObject {
        put(HttpSqlPaths.KEY_RESULT_COLNAMES, colNamesJsonArr)
        put(HttpSqlPaths.KEY_RESULT_ROWS, rowsJsonArray)
    }
}

fun ResultSet.toJsonArray(): JsonArray {
    return rowsToJsonArray(columnTypeMap())
}
