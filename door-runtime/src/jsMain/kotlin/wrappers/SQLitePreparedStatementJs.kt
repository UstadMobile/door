package wrappers

import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.ResultSet
import kotlin.js.Json
import kotlin.js.json

class SQLitePreparedStatementJs(private val connection: SQLiteConnectionJs): PreparedStatement {


    internal var statement: String? = null

    internal var params: Json = json()

    override suspend fun executeQueryAsync(): ResultSet {
        val result = connection.datasource.sendMessage(json("action" to "exec", "sql" to statement))
        return result.results?.let { SQLiteResultSet(it) } as ResultSet
    }

    override fun setString(index: Int, value: String) {
        TODO("Not yet implemented")
    }

    override fun executeUpdate(): Int {
        TODO("Not yet implemented")
    }

    override fun executeQuery(): ResultSet {
        throw Exception("This can not be used on JS, only for JVM")
    }

    override fun close() {}
}