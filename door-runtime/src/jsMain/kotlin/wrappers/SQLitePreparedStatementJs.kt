package wrappers

import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.ResultSet
import kotlin.js.json

class SQLitePreparedStatementJs(private val connection: SQLiteConnectionJs): PreparedStatement {


    internal var sqlStatement: String? = null

    internal var params: Array<String>? = null

    override suspend fun executeQueryAsync(): ResultSet {
        if(sqlStatement == null){
            throw Exception("Query can't be executed without sql statement")
        }
        val result = connection.datasource.sendMessage(
            json("action" to "exec", "sql" to sqlStatement, "params" to params))
        return result.results?.let { SQLiteResultSet(it) } as ResultSet
    }

    override fun setString(index: Int, value: String?) {
        addParam(index, value)
    }

    override fun setInt(index: Int, value: Int?) {
        addParam(index, value)
    }

    override fun setLong(index: Int, value: Long?) {
        addParam(index, value)
    }

    override fun executeUpdate(): Int {
        throw Exception("This can not be used on JS, only for JVM")
    }

    override suspend fun executeUpdateAsync(): Int {
        val result = connection.datasource.sendMessage(
            json("action" to "exec", "sql" to sqlStatement, "params" to params))
        return if(result.ready) 1 else 0
    }

    override fun executeQuery(): ResultSet {
        throw Exception("This can not be used on JS, only for JVM")
    }

    private fun addParam(index:Int, value: Any?){
        val mParams = params
        if (value != null && mParams != null && index > 0) {
            mParams[index - 1] = value.toString()
        }
    }

    override fun close() {
        params = null
        sqlStatement = null
    }
}