package wrappers

import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.types.BigDecimal
import com.ustadmobile.door.jdbc.types.Date
import com.ustadmobile.door.jdbc.types.Time
import com.ustadmobile.door.jdbc.types.TimeStamp
import io.ktor.utils.io.core.*

class SQLiteResultSet(private val results: Array<Any>): ResultSet {

    var currentIndex = -1

    var currentRow: Array<Any>? = null

    var columns: Array<String>? = null

    override fun next(): Boolean {
        return if(results.isNotEmpty()){
            currentIndex++
            val data = results.first().asDynamic().values
            columns = results.first().asDynamic().columns
            val hasNext = currentIndex < data.length as Int
            currentRow = if(hasNext) data[currentIndex] else null
            return currentRow != null
        }else{
            false
        }
    }

    override fun getString(columnName: String): String? {
        val value = getValue(columnName)
        if(value != null){
            return value.toString()
        }
        return null
    }

    override fun getBoolean(columnName: String): Boolean {
        return getValue(columnName).toString().toBoolean()
    }

    override fun getByte(columnName: String): Byte {
        return getValue(columnName).toString().toByte()
    }

    override fun getShort(columnName: String): Short {
        return getValue(columnName).toString().toShort()
    }

    override fun getInt(columnName: String): Int {
        return getValue(columnName).toString().toInt()
    }

    override fun getFloat(columnName: String): Float {
        return getValue(columnName).toString().toFloat()
    }

    override fun getLong(columnName: String): Long {
        return getValue(columnName).toString().toLong()
    }

    override fun getDouble(columnName: String): Double {
       return getValue(columnName).toString().toDouble()
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
        return Date(getValue(columnName).toString())
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

    private fun getValue(columnName: String): Any?{
        return columns?.indexOf(columnName)?.let { currentRow?.get(it)}
    }

    override fun close() {
        currentRow = null
        columns = null
        currentIndex = -1
    }

}