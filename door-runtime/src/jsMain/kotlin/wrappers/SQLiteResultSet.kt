package wrappers

import com.ustadmobile.door.jdbc.ResultSet

class SQLiteResultSet(private val results: Array<Any>): ResultSet {

    var currentIndex = -1

    var currentRow: Array<Any>? = null

    override fun next(): Boolean {
        return if(results.isNotEmpty()){
            currentIndex++
            val data = results.first().asDynamic().values
            val hasNext = currentIndex < data.length as Int
            currentRow = if(hasNext) data[currentIndex] else null
            return currentRow != null
        }else{
            false
        }
    }

    override fun getString(columnIndex: Int): String? {
        return currentRow?.get(columnIndex)?.toString()
    }

    override fun close() {}

}