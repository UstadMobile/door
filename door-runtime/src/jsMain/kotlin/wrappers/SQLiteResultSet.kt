package wrappers

import com.ustadmobile.door.jdbc.ResultSet

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
        return columns?.indexOf(columnName)?.let { currentRow?.get(it)?.toString() }
    }

    override fun close() {
        currentRow = null
        columns = null
        currentIndex = -1
    }

}