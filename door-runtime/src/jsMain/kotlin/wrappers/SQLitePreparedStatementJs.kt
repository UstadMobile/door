package wrappers

import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.types.Date
import kotlin.js.json

class SQLitePreparedStatementJs(private val connection: SQLiteConnectionJs): PreparedStatement {

    internal var sqlStatement: String? = null

    internal var params: Array<Any>? = null

    override suspend fun executeQueryAsyncInt(): ResultSet {
        if(sqlStatement == null){
            throw Exception("Query can't be executed without sql statement")
        }
        val result = connection.datasource.sendMessage(
            json("action" to "exec", "sql" to sqlStatement, "params" to params))
        return result.results?.let { SQLiteResultSet(it) } as ResultSet
    }

    override fun setBoolean(index: Int, value: Boolean) {
        addParam(index, value)
    }

    override fun setByte(index: Int, value: Byte) {
        addParam(index, value)
    }

    override fun setShort(index: Int, value: Short) {
        addParam(index, value)
    }

    override fun setString(index: Int, value: String) {
        addParam(index, value)
    }

    override fun setBytes(index: Int, value: ByteArray) {
        addParam(index, value)
    }

    override fun setDate(index: Int, value: Date) {
        addParam(index, value)
    }

    override fun setTime(index: Int, value: Any) {
        addParam(index, value)
    }

    override fun setTimestamp(index: Int, value: Any) {
        addParam(index, value)
    }

    override fun setObject(index: Int, value: Any) {
        addParam(index, value)
    }

    override fun setInt(index: Int, value: Int) {
        addParam(index, value)
    }

    override fun setLong(index: Int, value: Long) {
        addParam(index, value)
    }

    override fun setFloat(index: Int, value: Float) {
        addParam(index, value)
    }

    override fun setDouble(index: Int, value: Double) {
        addParam(index, value)
    }

    override fun setBigDecimal(index: Int, value: Any) {
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
            mParams[index - 1] = value
        }
    }

    override fun close() {
        params = null
        sqlStatement = null
    }
}