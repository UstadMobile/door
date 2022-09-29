package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.ext.rowsToJsonArray
import com.ustadmobile.door.jdbc.ResultSet
import kotlinx.serialization.json.JsonArray

/**
 * Map of the column name to the Sql Types (e.g. TypesKmp) value for the column type
 */
fun ResultSet.columnTypeMap(): Map<String, Int> {
    val metaDataVal = metaData
    return (1 .. metaDataVal.columnCount).map {
        metaDataVal.getColumnLabel(it) to metaDataVal.getColumnType(it)
    }.toMap()
}

fun ResultSet.toJsonArray(): JsonArray {
    return rowsToJsonArray(columnTypeMap())
}
