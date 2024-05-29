package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.TypesKmp
import com.ustadmobile.door.jdbc.ext.*
import com.ustadmobile.door.replication.JsonDbFieldInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive


/**
 * Get a column value from the ResultSet as a JsonPrimitive
 *
 * @param fieldInfo
 *
 * @return JsonPrimitive representing the column value
 */
fun ResultSet.getJsonPrimitive(
    fieldInfo: JsonDbFieldInfo,
) : JsonPrimitive{
    val colName = fieldInfo.fieldName
    return if(fieldInfo.nullable) {
        when(fieldInfo.dbFieldType) {
            TypesKmp.SMALLINT -> JsonPrimitive(getShortNullable(colName))
            TypesKmp.INTEGER -> JsonPrimitive(getIntNullable(colName))
            TypesKmp.BIGINT -> JsonPrimitive(getLongNullable(colName))
            TypesKmp.REAL -> JsonPrimitive(getFloatNullable(colName))
            TypesKmp.FLOAT -> JsonPrimitive(getFloatNullable(colName))
            TypesKmp.DOUBLE -> JsonPrimitive(getDoubleNullable(colName))
            TypesKmp.VARCHAR -> JsonPrimitive(getString(colName))
            TypesKmp.LONGVARCHAR -> JsonPrimitive(getString(colName))
            TypesKmp.BOOLEAN -> JsonPrimitive(getBooleanNullable(colName))
            else -> throw IllegalArgumentException("Unsupported type: ${fieldInfo.dbFieldType}")
        }
    }else {
        when(fieldInfo.dbFieldType) {
            TypesKmp.SMALLINT -> JsonPrimitive(getShort(colName))
            TypesKmp.INTEGER -> JsonPrimitive(getInt(colName))
            TypesKmp.BIGINT -> JsonPrimitive(getLong(colName))
            TypesKmp.REAL -> JsonPrimitive(getFloat(colName))
            TypesKmp.FLOAT -> JsonPrimitive(getFloat(colName))
            TypesKmp.DOUBLE -> JsonPrimitive(getDouble(colName))
            TypesKmp.VARCHAR -> JsonPrimitive(getString(colName))
            TypesKmp.LONGVARCHAR -> JsonPrimitive(getString(colName))
            TypesKmp.BOOLEAN -> JsonPrimitive(getBoolean(colName))
            else -> throw IllegalArgumentException("Unsupported type: ${fieldInfo.dbFieldType}")
        }
    }
}

/**
 * Convert the current row to a JsonObject
 *
 * @param colTypeMap column types that should be found on each row
 * as a map of the column name to the type as per TypesKmp
 */
fun ResultSet.rowToJsonObject(columns: List<JsonDbFieldInfo>): JsonObject {
    return JsonObject(
        columns.associate {
            it.fieldName to getJsonPrimitive(it)
        }
    )
}

/**
 * Used by generated code to make a map of column name to the index of the column. This is used by generated code.
 */
fun ResultSet.columnIndexMap() : Map<String, Int> {
    return getMetaData().let { metaData ->
        (1 .. metaData.getColumnCount()).map { metaData.getColumnLabel(it) to it }.toMap()
    }
}