package wrappers

import com.ustadmobile.door.jdbc.*

open class SqliteStatementJs(
    protected val connection: SQLiteConnectionJs,
    val autoGeneratedKeys: Int = StatementConstantsKmp.NO_GENERATED_KEYS
) : Statement {

    private var closed: Boolean = false

    override fun executeUpdate(sql: String): Int {
        throw SQLException("Synchronous SQL not supported!")
    }

    override fun close() {
        //nothing to do really
        closed = true
    }

    override fun isClosed() = closed

    override fun getConnection() = connection

    override fun getGeneratedKeys(): ResultSet {
        TODO("Not yet implemented")
    }
}