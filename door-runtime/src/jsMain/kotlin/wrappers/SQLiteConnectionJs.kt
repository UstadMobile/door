package wrappers

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.PreparedStatement
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.js.json

class SQLiteConnectionJs(val datasource: SQLiteDatasourceJs):Connection {

    override fun setAutoCommit(commit: Boolean) {}

    override fun prepareStatement(param: String?): PreparedStatement {
        return SQLitePreparedStatementJs(this).apply {
            sqlStatement = param
            params = arrayOf()
        }
    }

    override fun commit() {}

    override fun close() {
        GlobalScope.launch {
            datasource.sendMessage(json("action" to "close"))
        }
    }

}