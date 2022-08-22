package com.ustadmobile.door.roomjdbc

import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal
import java.net.URL
import java.sql.*
import java.sql.Array
import java.sql.Date
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max

class PreparedStatementRoomJdbc(
    private val querySql: String,
    roomConnection: ConnectionRoomJdbc,
    private val autoGenerateKeys: Int = PreparedStatement.NO_GENERATED_KEYS,
) : StatementRoomJdbc(roomConnection), PreparedStatement, SupportSQLiteQuery {

    private val bindingTypes = mutableMapOf<Int, Int>()

    private val boundLongs = mutableMapOf<Int, Long>()

    private val boundDoubles = mutableMapOf<Int, Double>()

    private val boundStrings = mutableMapOf<Int, String?>()

    private var numArgs = 0

    private var lastGeneratedKey: Long = 0

    private var closeStmt: Boolean = false

    private val compiledStmt: SupportSQLiteStatement by lazy {
        closeStmt = true
        roomConnection.roomDb.openHelper.writableDatabase.compileStatement(querySql)
    }

    private fun ensureCapacity(paramCount: Int) {
        numArgs = max(numArgs, paramCount)
    }

    override fun getSql(): String = querySql

    override fun bindTo(statement: SupportSQLiteProgram) {
        for(index in 0 until numArgs) {
            when(bindingTypes[index]) {
                NULL -> statement.bindNull(index + 1)
                LONG -> statement.bindLong(index + 1, boundLongs[index]!!)
                DOUBLE -> statement.bindDouble(index + 1, boundDoubles[index]!!)
                STRING -> {
                    if(boundStrings[index] != null) {
                        statement.bindString(index + 1, boundStrings[index])
                    }else {
                        statement.bindNull(index + 1)
                    }
                }
            }
        }
    }

    override fun close() {
        if(closeStmt)
            compiledStmt.close()
    }

    override fun getArgCount(): Int = numArgs

    override fun executeQuery(): ResultSet {
        return ResultSetRoomJdbc(roomConnection.roomDb.query(this), this)
    }

    override fun executeUpdate(): Int {
        bindTo(compiledStmt)
        if(autoGenerateKeys != PreparedStatement.NO_GENERATED_KEYS) {
            return compiledStmt.executeUpdateDelete()
        }else {
            lastGeneratedKey = compiledStmt.executeInsert()
            return 1
        }
    }

    override fun execute(): Boolean {
        compiledStmt.execute()
        return true
    }

    override fun addBatch() {
        TODO("Not yet implemented")
    }

    override fun setNull(parameterIndex: Int, sqlType: Int) {
        ensureCapacity(parameterIndex + 1)
        bindingTypes[parameterIndex - 1] = NULL
    }

    override fun setNull(parameterIndex: Int, sqlType: Int, typeName: String?) {
        TODO("Not yet implemented")
    }

    override fun setBoolean(parameterIndex: Int, x: Boolean) {
        ensureCapacity(parameterIndex + 1)
        bindingTypes[parameterIndex - 1] = LONG
        boundLongs[parameterIndex -1] = if(x) 1L else 0L
    }

    override fun setByte(parameterIndex: Int, x: Byte) {
        ensureCapacity(parameterIndex + 1)
        bindingTypes[parameterIndex - 1] = LONG
        boundLongs[parameterIndex - 1] = x.toLong()
    }

    override fun setShort(parameterIndex: Int, x: Short) {
        ensureCapacity(parameterIndex + 1)
        bindingTypes[parameterIndex - 1] = LONG
        boundLongs[parameterIndex - 1] = x.toLong()
    }

    override fun setInt(parameterIndex: Int, x: Int) {
        ensureCapacity(parameterIndex + 1)
        bindingTypes[parameterIndex - 1] = LONG
        boundLongs[parameterIndex - 1] = x.toLong()
    }

    override fun setLong(parameterIndex: Int, x: Long) {
        ensureCapacity(parameterIndex + 1)
        bindingTypes[parameterIndex - 1] = LONG
        boundLongs[parameterIndex - 1] = x
    }

    override fun setFloat(parameterIndex: Int, x: Float) {
        ensureCapacity(parameterIndex + 1)
        bindingTypes[parameterIndex - 1] = DOUBLE
        boundDoubles[parameterIndex - 1] = x.toDouble()
    }

    override fun setDouble(parameterIndex: Int, x: Double) {
        ensureCapacity(parameterIndex + 1)
        bindingTypes[parameterIndex - 1] = DOUBLE
        boundDoubles[parameterIndex - 1] = x
    }

    override fun setBigDecimal(parameterIndex: Int, x: BigDecimal?) {
        TODO("Not yet implemented")
    }

    override fun setString(parameterIndex: Int, x: String?) {
        ensureCapacity(parameterIndex + 1)
        if(x != null) {
            bindingTypes[parameterIndex -1] = STRING
            boundStrings[parameterIndex -1] = x
        }else {
            bindingTypes[parameterIndex - 1] = NULL
        }
    }

    override fun setBytes(parameterIndex: Int, x: ByteArray?) {
        TODO("Not yet implemented")
    }

    override fun setDate(parameterIndex: Int, x: Date?) {
        TODO("Not yet implemented")
    }

    override fun setDate(parameterIndex: Int, x: Date?, cal: Calendar?) {
        TODO("Not yet implemented")
    }

    override fun setTime(parameterIndex: Int, x: Time?) {
        TODO("Not yet implemented")
    }

    override fun setTime(parameterIndex: Int, x: Time?, cal: Calendar?) {
        TODO("Not yet implemented")
    }

    override fun setTimestamp(parameterIndex: Int, x: Timestamp?) {
        TODO("Not yet implemented")
    }

    override fun setTimestamp(parameterIndex: Int, x: Timestamp?, cal: Calendar?) {
        TODO("Not yet implemented")
    }

    override fun setAsciiStream(parameterIndex: Int, x: InputStream?, length: Int) {
        TODO("Not yet implemented")
    }

    override fun setAsciiStream(parameterIndex: Int, x: InputStream?, length: Long) {
        TODO("Not yet implemented")
    }

    override fun setAsciiStream(parameterIndex: Int, x: InputStream?) {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun setUnicodeStream(parameterIndex: Int, x: InputStream?, length: Int) {
        TODO("Not yet implemented")
    }

    override fun setBinaryStream(parameterIndex: Int, x: InputStream?, length: Int) {
        TODO("Not yet implemented")
    }

    override fun setBinaryStream(parameterIndex: Int, x: InputStream?, length: Long) {
        TODO("Not yet implemented")
    }

    override fun setBinaryStream(parameterIndex: Int, x: InputStream?) {
        TODO("Not yet implemented")
    }

    override fun clearParameters() {
        TODO("Not yet implemented")
    }

    override fun setObject(parameterIndex: Int, x: Any?, targetSqlType: Int) {
        TODO("Not yet implemented")
    }

    override fun setObject(parameterIndex: Int, x: Any?) {
        if(x == null) {
            ensureCapacity(parameterIndex + 1)
            bindingTypes[parameterIndex - 1] = NULL
        }else {
            throw IllegalArgumentException("setObject on Room JDBC only supports null")
        }
    }

    override fun setObject(parameterIndex: Int, x: Any?, targetSqlType: Int, scaleOrLength: Int) {
        TODO("Not yet implemented")
    }

    override fun setCharacterStream(parameterIndex: Int, reader: Reader?, length: Int) {
        TODO("Not yet implemented")
    }

    override fun setCharacterStream(parameterIndex: Int, reader: Reader?, length: Long) {
        TODO("Not yet implemented")
    }

    override fun setCharacterStream(parameterIndex: Int, reader: Reader?) {
        TODO("Not yet implemented")
    }

    override fun setRef(parameterIndex: Int, x: Ref?) {
        TODO("Not yet implemented")
    }

    override fun setBlob(parameterIndex: Int, x: Blob?) {
        TODO("Not yet implemented")
    }

    override fun setBlob(parameterIndex: Int, inputStream: InputStream?, length: Long) {
        TODO("Not yet implemented")
    }

    override fun setBlob(parameterIndex: Int, inputStream: InputStream?) {
        TODO("Not yet implemented")
    }

    override fun setClob(parameterIndex: Int, x: Clob?) {
        TODO("Not yet implemented")
    }

    override fun setClob(parameterIndex: Int, reader: Reader?, length: Long) {
        TODO("Not yet implemented")
    }

    override fun setClob(parameterIndex: Int, reader: Reader?) {
        TODO("Not yet implemented")
    }

    override fun setArray(parameterIndex: Int, x: Array?) {
        TODO("Not yet implemented")
    }

    override fun getMetaData(): ResultSetMetaData {
        TODO("Not yet implemented")
    }

    override fun setURL(parameterIndex: Int, x: URL?) {
        TODO("Not yet implemented")
    }

    override fun getParameterMetaData(): ParameterMetaData {
        TODO("Not yet implemented")
    }

    override fun setRowId(parameterIndex: Int, x: RowId?) {
        TODO("Not yet implemented")
    }

    override fun setNString(parameterIndex: Int, value: String?) {
        TODO("Not yet implemented")
    }

    override fun setNCharacterStream(parameterIndex: Int, value: Reader?, length: Long) {
        TODO("Not yet implemented")
    }

    override fun setNCharacterStream(parameterIndex: Int, value: Reader?) {
        TODO("Not yet implemented")
    }

    override fun setNClob(parameterIndex: Int, value: NClob?) {
        TODO("Not yet implemented")
    }

    override fun setNClob(parameterIndex: Int, reader: Reader?, length: Long) {
        TODO("Not yet implemented")
    }

    override fun setNClob(parameterIndex: Int, reader: Reader?) {
        TODO("Not yet implemented")
    }

    override fun setSQLXML(parameterIndex: Int, xmlObject: SQLXML?) {
        TODO("Not yet implemented")
    }

    companion object {
        //Supported types for SQLite on Android
        const val NULL = 1
        const val LONG = 2
        const val DOUBLE = 3
        const val STRING = 4
        const val BLOG = 5
    }
}