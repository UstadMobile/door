package com.ustadmobile.door.httpsql

import com.ustadmobile.door.httpsql.HttpSqlPaths.KEY_RESULT_COLNAMES
import com.ustadmobile.door.httpsql.HttpSqlPaths.KEY_RESULT_ROWS
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.ResultSetMetaData
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.jdbc.types.BigDecimal
import com.ustadmobile.door.jdbc.types.Date
import com.ustadmobile.door.jdbc.types.Time
import com.ustadmobile.door.jdbc.types.TimeStamp
import kotlinx.serialization.json.*

/**
 * The JSON object that comes back from the server for a result should be structured as follows:
 *
 * {
 *    colNames: ["id", "name"...]
 *    results:[
 *      [0, "bob"]
 *      [1, "amy"]
 *    ]
 * }
 */
class HttpSqlResultSet(
    jsonResults: JsonObject
): ResultSet {

    private var currentRowIndex: Int = -1

    private var wasNull = false

    private val resultsArray: JsonArray = jsonResults.get(KEY_RESULT_ROWS)?.jsonArray
        ?: throw IllegalArgumentException("HttpSqlResultSet has no rows object")

    private val colNames: List<String> = jsonResults[KEY_RESULT_COLNAMES]?.jsonArray?.map { it.jsonPrimitive.content }
        ?: throw IllegalArgumentException("HttpSqlResultSet has no column names!")

    private val currentRow: JsonArray
        get() = resultsArray[currentRowIndex].jsonArray

    private var closed = false

    private inner class HttpSqlResultSetMetaData: ResultSetMetaData{

        override fun getColumnCount() = resultsArray.size

        override fun getColumnLabel(column: Int) = colNames[column]
    }


    override fun next(): Boolean {
        return if(currentRowIndex + 1 < resultsArray.size) {
            currentRowIndex++
            true
        }else {
            false
        }
    }

    private fun colIndex(columnName: String) : Int {
        val colIndex = colNames.indexOf(columnName)
        if(colIndex == -1) {
            throw SQLException("HttpSqlResultSet: No such column: $columnName")
        }

        return colIndex + 1 //JDBC get starts at 1, not 0
    }

    override fun getString(columnName: String): String? {
        return getString(colIndex(columnName))
    }

    override fun getString(columnIndex: Int): String? {
        return currentRow[columnIndex - 1].jsonPrimitive.contentOrNull.also {
            wasNull = it == null
        }
    }

    override fun getBoolean(columnName: String): Boolean {
        return getBoolean(colIndex(columnName))
    }

    override fun getBoolean(columnIndex: Int): Boolean {
        return currentRow[columnIndex - 1].jsonPrimitive.booleanOrNull.also {
            wasNull = it == null
        } ?: false
    }

    override fun getByte(columnName: String): Byte {
        return getByte(colIndex(columnName))
    }

    override fun getByte(columnIndex: Int): Byte {
        return currentRow[columnIndex - 1].jsonPrimitive.intOrNull.also {
            wasNull = it == null
        }?.toByte() ?: 0
    }

    override fun getShort(columnName: String): Short {
        return getShort(colIndex(columnName))
    }

    override fun getShort(columnIndex: Int): Short {
        return currentRow[columnIndex - 1].jsonPrimitive.intOrNull.also {
            wasNull  = it == null
        }?.toShort() ?: 0
    }

    override fun getInt(columnName: String): Int {
        return getInt(colIndex(columnName))
    }

    override fun getInt(columnIndex: Int): Int {
        return currentRow[columnIndex - 1].jsonPrimitive.intOrNull.also {
            wasNull = it == null
        } ?: 0
    }

    override fun getLong(columnName: String): Long {
        return getLong(colIndex(columnName))
    }

    override fun getLong(columnIndex: Int): Long {
        return currentRow[columnIndex - 1].jsonPrimitive.longOrNull.also {
            wasNull = it == null
        } ?: 0
    }

    override fun getFloat(columnName: String): Float {
        return getFloat(colIndex(columnName))
    }

    override fun getFloat(columnIndex: Int): Float {
        return currentRow[columnIndex - 1].jsonPrimitive.floatOrNull.also {
            wasNull = it == null
        } ?: 0.toFloat()
    }

    override fun getDouble(columnName: String): Double {
        return getDouble(colIndex(columnName))
    }

    override fun getDouble(columnIndex: Int): Double {
        return currentRow[columnIndex - 1].jsonPrimitive.doubleOrNull.also {
            wasNull = it == null
        } ?: 0.toDouble()
    }

    override fun getBigDecimal(columnName: String): BigDecimal? {
        throw SQLException("Not supported")
    }

    override fun getBytes(columnName: String): ByteArray? {
        throw SQLException("Not supported")
    }

    override fun getDate(columnName: String): Date? {
        throw SQLException("Not supported")
    }

    override fun getTime(columnName: String): Time? {
        throw SQLException("Not supported")
    }

    override fun getTimestamp(columnName: String): TimeStamp? {
        throw SQLException("Not supported")
    }

    override fun getObject(columnName: String): Any? {
        throw SQLException("Not supported")
    }

    override fun getObject(columnIndex: Int): Any? {
        throw SQLException("Not supported")
    }

    override fun wasNull() = wasNull

    override fun getMetaData(): ResultSetMetaData {
        return HttpSqlResultSetMetaData()
    }

    override fun close() {
        closed = true
    }

    override fun isClosed() = closed

}