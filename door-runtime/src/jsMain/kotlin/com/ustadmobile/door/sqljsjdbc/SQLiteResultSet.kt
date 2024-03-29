package com.ustadmobile.door.sqljsjdbc

import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.ResultSetMetaData
import com.ustadmobile.door.jdbc.types.BigDecimal
import com.ustadmobile.door.jdbc.types.Date
import com.ustadmobile.door.jdbc.types.Time
import com.ustadmobile.door.jdbc.types.TimeStamp
import io.ktor.utils.io.core.*

@Suppress("UnsafeCastFromDynamic")
class SQLiteResultSet(private val results: Array<Any>): ResultSet {

    inner class MetaData(): ResultSetMetaData {
        override fun getColumnCount(): Int {
            return columns?.size ?: 0
        }

        /**
         * Indexes are 0 based on JS while on JVM is 1 based
         */
        override fun getColumnLabel(column: Int): String {
            val index = column - 1
            return columns?.get(index) ?: throw IllegalStateException("Could not get column index: $index")
        }
    }


    var currentIndex = -1

    var currentRow: Array<Any>? = null

    var columns: Array<String>? = null

    private var closed = false

    private var lastWasNull = true

    init {
        columns = if(results.isNotEmpty()) results.first().asDynamic().columns else null
    }


    override fun next(): Boolean {
        return if(results.isNotEmpty()){
            currentIndex++
            val data = results.first().asDynamic().values
            val hasNext = currentIndex < data.length as Int
            currentRow = if(hasNext) data[currentIndex] else null
            if(currentRow == null){
                currentIndex--
            }
            return currentRow != null
        }else{
            false
        }
    }

    override fun getString(columnName: String): String? {
        return getValue(columnName)?.toString()
    }

    override fun getString(columnIndex: Int): String? {
        return getValue(columnIndex)?.toString()
    }

    @Suppress("UNUSED_VARIABLE") //value is used on JS
    override fun getBoolean(columnName: String): Boolean {
        val value = getValue(columnName)
        return js("Boolean(value)")
    }

    @Suppress("UNUSED_VARIABLE") //value is used on JS
    override fun getBoolean(columnIndex: Int): Boolean {
        val value = getValue(columnIndex)
        return js("Boolean(value)")
    }

    override fun getByte(columnName: String): Byte {
        return getValue(columnName)?.toString()?.toByte() ?: 0
    }

    override fun getByte(columnIndex: Int): Byte {
        return getValue(columnIndex)?.toString()?.toByte() ?: 0
    }

    override fun getShort(columnName: String): Short {
        return getValue(columnName)?.toString()?.toShort() ?: 0
    }

    override fun getShort(columnIndex: Int): Short {
        return getValue(columnIndex)?.toString()?.toShort() ?: 0
    }

    override fun getInt(columnName: String): Int {
        return getValue(columnName)?.toString()?.toInt() ?: 0
    }

    override fun getInt(columnIndex: Int): Int {
        return getValue(columnIndex)?.toString()?.toInt() ?: 0
    }

    override fun getFloat(columnName: String): Float {
        return getValue(columnName)?.toString()?.toFloat() ?: 0f
    }

    override fun getFloat(columnIndex: Int): Float {
        return getValue(columnIndex)?.toString()?.toFloat() ?: 0f
    }

    override fun getLong(columnName: String): Long {
        return getValue(columnName)?.toString()?.toLong() ?: 0L
    }

    override fun getLong(columnIndex: Int): Long {
        return getValue(columnIndex)?.toString()?.toLong() ?: 0L
    }

    override fun getDouble(columnName: String): Double {
       return getValue(columnName)?.toString()?.toDouble() ?: 0.0
    }

    override fun getDouble(columnIndex: Int): Double {
        return getValue(columnIndex)?.toString()?.toDouble() ?: 0.0
    }

    override fun getBigDecimal(columnName: String): BigDecimal? {
        return getValue(columnName)
    }

    override fun getBytes(columnName: String): ByteArray? {
        val value = getValue(columnName)
        if(value != null){
            return value.toString().toByteArray()
        }
        return null
    }

    override fun getDate(columnName: String): Date? {
        val value = getValue(columnName)
        if(value != null){
            return Date(value.toString())
        }
        return null
    }

    override fun getTime(columnName: String): Time? {
       return getValue(columnName)
    }

    override fun getTimestamp(columnName: String): TimeStamp? {
        return getValue(columnName)
    }

    override fun getObject(columnName: String): Any? {
        return getValue(columnName)
    }

    override fun getObject(columnIndex: Int): Any? {
        return getValue(columnIndex)
    }

    override fun wasNull() = lastWasNull

    override fun getMetaData() = MetaData()

    private fun getValue(columnName: String): Any?{
        return columns?.indexOf(columnName)?.let { currentRow?.get(it) }.also {
            lastWasNull = it == null
        }
    }

    private fun getValue(columnIndex: Int): Any? {
        return currentRow?.get(columnIndex - 1).also {
            lastWasNull = it == null
        }
    }

    override fun close() {
        currentRow = null
        columns = null
        currentIndex = -1
        closed = true
    }

    override fun isClosed() = closed

    override fun toString(): String {
        var str = "[SQLiteResultSet] Columns=$columns"

        if(results.isEmpty()) {
            str += " - no rows"
            return str
        }

        return str
    }

}