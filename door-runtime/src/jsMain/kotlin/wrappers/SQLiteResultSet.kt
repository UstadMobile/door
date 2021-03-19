package wrappers

import com.ustadmobile.door.jdbc.ResultSet

class SQLiteResultSet(private val results: Array<Array<Any>>): ResultSet {

    var currentIndex = 0

    override fun next(): Boolean {
        return if(results.size > 2){
            currentIndex++
            results[0].isNotEmpty()
        }else{
            false
        }
    }

    override fun getString(index: Int): String {
        TODO("Not yet implemented")
    }

    override fun close() {}

}