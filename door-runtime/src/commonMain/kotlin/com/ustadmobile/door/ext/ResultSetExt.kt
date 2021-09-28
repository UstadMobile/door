package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.TypesKmp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun <R> ResultSet.mapRows(block: (ResultSet) -> R): List<R> {
    val mappedResults = mutableLinkedListOf<R>()
    while(next()) {
        mappedResults += block(this)
    }

    return mappedResults
}

/**
 * Get a column value from the ResultSet as a JsonPrimitive
 *
 * @param colName column name
 * @param colType Int representing the column type expected as per TypesKmp
 *
 * @return JsonPrimitive representing the column value
 */
fun ResultSet.getJsonPrimitive(colName: String, colType: Int) : JsonPrimitive{
    return when(colType) {
        TypesKmp.SMALLINT -> JsonPrimitive(getShort(colName))
        TypesKmp.INTEGER -> JsonPrimitive(getInt(colName))
        TypesKmp.BIGINT -> JsonPrimitive(getLong(colName))
        TypesKmp.REAL -> JsonPrimitive(getFloat(colName))
        TypesKmp.FLOAT -> JsonPrimitive(getFloat(colName))
        TypesKmp.DOUBLE -> JsonPrimitive(getDouble(colName))
        TypesKmp.VARCHAR -> JsonPrimitive(getString(colName))
        TypesKmp.LONGVARCHAR -> JsonPrimitive(getString(colName))
        else -> throw IllegalArgumentException("Unsupported type: $colType")
    }
}

/**
 * @param colTypeMap column types that should be found on each row
 * as a map of the column name to the type as per TypesKmp
 *
 * @return JsonArray of JsonObject where each row is converted to a JSON object
 */
fun ResultSet.rowsToJsonArray(colTypeMap: Map<String, Int>): JsonArray {
    return JsonArray(mapRows {
        rowToJsonObject(colTypeMap)
    })
}

/**
 * Convert the current row to a JsonObject
 *
 * @param colTypeMap column types that should be found on each row
 * as a map of the column name to the type as per TypesKmp
 */
fun ResultSet.rowToJsonObject(colTypeMap: Map<String, Int>): JsonObject {
    return JsonObject(colTypeMap.entries.map { it.key to getJsonPrimitive(it.key, it.value)}.toMap())
}

/**
 * Used by generated code to make a map of column name to the index of the column. This is used by generated code.
 */
fun ResultSet.columnIndexMap() : Map<String, Int> {
    return getMetaData().let { metaData ->
        (1 .. metaData.getColumnCount()).map { metaData.getColumnLabel(it) to it }.toMap()
    }
}