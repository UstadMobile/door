package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.TypesKmp
import com.ustadmobile.door.jdbc.ext.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive


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
        TypesKmp.SMALLINT -> JsonPrimitive(getShortNullable(colName))
        TypesKmp.INTEGER -> JsonPrimitive(getIntNullable(colName))
        TypesKmp.BIGINT -> JsonPrimitive(getLongNullable(colName))
        TypesKmp.REAL -> JsonPrimitive(getFloatNullable(colName))
        TypesKmp.FLOAT -> JsonPrimitive(getFloatNullable(colName))
        TypesKmp.DOUBLE -> JsonPrimitive(getDoubleNullable(colName))
        TypesKmp.VARCHAR -> JsonPrimitive(getString(colName))
        TypesKmp.LONGVARCHAR -> JsonPrimitive(getString(colName))
        TypesKmp.BOOLEAN -> JsonPrimitive(getBooleanNullable(colName))
        else -> throw IllegalArgumentException("Unsupported type: $colType")
    }
}

/**
 * Get a column value from teh ResultSet as a JsonPrimitive
 * @param columnIndex index in the ResultSet
 * @param colType Int representing the column type expected as per TypesKmp
 *
 * @return JsonPrimitive representing the column value
 */
fun ResultSet.getJsonPrimitive(columnIndex: Int, colType: Int) : JsonPrimitive {
    return when(colType) {
        TypesKmp.SMALLINT -> JsonPrimitive(getShortNullable(columnIndex))
        TypesKmp.INTEGER -> JsonPrimitive(getIntNullable(columnIndex))
        TypesKmp.BIGINT -> JsonPrimitive(getLongNullable(columnIndex))
        TypesKmp.REAL -> JsonPrimitive(getFloatNullable(columnIndex))
        TypesKmp.FLOAT -> JsonPrimitive(getFloatNullable(columnIndex))
        TypesKmp.DOUBLE -> JsonPrimitive(getDoubleNullable(columnIndex))
        TypesKmp.VARCHAR -> JsonPrimitive(getString(columnIndex))
        TypesKmp.LONGVARCHAR -> JsonPrimitive(getString(columnIndex))
        TypesKmp.BOOLEAN -> JsonPrimitive(getBooleanNullable(columnIndex))
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