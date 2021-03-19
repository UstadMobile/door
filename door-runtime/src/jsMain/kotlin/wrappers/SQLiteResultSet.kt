package wrappers

import com.ustadmobile.door.jdbc.ResultSet

class SQLiteResultSet(private val results: Array<Any>): ResultSet {

    var currentIndex = 0

    var nextRow: Array<Any>? = null

    override fun next(): Boolean {
        return if(results.isNotEmpty()){
            currentIndex++
            val data = results.first().asDynamic().values
            val hasNext = data.size < currentIndex
            nextRow = if(hasNext) data[currentIndex] else null
            return nextRow != null
        }else{
            false
        }
    }

    override fun getString(columnIndex: Int): String {
        return nextRow?.get(columnIndex).toString()
    }

    override fun close() {}

}