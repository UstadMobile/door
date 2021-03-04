package com.ustadmobile.door

import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal
import java.net.URL
import java.sql.*
import java.sql.Array
import java.sql.Date
import java.util.*

/**
 * Some JDBC drivers do not support java.sql.Array . This proxy class is a workaround that will
 * generate a new PreparedStatement under the hood for each time it is invoked, and substitutes a
 * single array parameter ? for the number of elements in the array, and then sets them all
 * individually. This will not deliver the same performance, but it will execute the query.
 *
 * This class can be used roughly as follows:
 *
 * int[] myUids = new int[]{1,2,3};
 * Array myArray = PreparedStatementArrayProxy.createArrayOf("JDBCTYPE", myUids);
 * PreparedStatement preparedStmt = new PreparedStatementArrayProxy("SELECT * FROM TABLE WHERE UID in (?)", connection);
 * preparedStmt.setArray(1, myArray);
 * ResultSet result = preparedStmt.executeQuery();
 *
 */
class PreparedStatementArrayProxy
/**
 * Create a new PreparedStatementArrayProxy
 *
 * @param query The query to execute (as per a normal PreparedStatement using ? for parameters)
 * @param connection the JDBC connection to run the query on
 */
(private val query: String, private val connection: Connection) : PreparedStatement {

    private val queryParams = TreeMap<Int, Any>()

    private val queryTypes = TreeMap<Int, Int>()

    private class JdbcArrayProxy(private val typeName: String, val objects: kotlin.Array<out Any?>) : Array {

        private var baseType: Int = 0

        init {

            when (typeName) {
                "INTEGER" -> baseType = Types.INTEGER
                "VARCHAR" -> baseType = Types.VARCHAR
                "BIGINT" -> baseType = Types.BIGINT
                "SHORT" -> baseType = Types.SMALLINT
                "BOOLEAN" -> baseType = Types.BOOLEAN
                "TEXT" -> baseType = Types.LONGVARCHAR
            }
        }

        @Throws(SQLException::class)
        override fun getBaseTypeName(): String {
            return typeName
        }

        @Throws(SQLException::class)
        override fun getBaseType(): Int {
            return baseType
        }

        @Throws(SQLException::class)
        override fun getArray(): Any {
            return this
        }

        @Throws(SQLException::class)
        override fun getArray(map: Map<String, Class<*>>): Any? {
            return null
        }

        @Throws(SQLException::class)
        override fun getArray(l: Long, i: Int): Any? {
            return null
        }

        @Throws(SQLException::class)
        override fun getArray(l: Long, i: Int, map: Map<String, Class<*>>): Any? {
            return null
        }

        @Throws(SQLException::class)
        override fun getResultSet(): ResultSet? {
            return null
        }

        @Throws(SQLException::class)
        override fun getResultSet(map: Map<String, Class<*>>): ResultSet? {
            return null
        }

        @Throws(SQLException::class)
        override fun getResultSet(l: Long, i: Int): ResultSet? {
            return null
        }

        @Throws(SQLException::class)
        override fun getResultSet(l: Long, i: Int, map: Map<String, Class<*>>): ResultSet? {
            return null
        }

        @Throws(SQLException::class)
        override fun free() {

        }
    }

    @Throws(SQLException::class)
    protected fun prepareStatement(): PreparedStatement {
        var arrayOffset = 0
        val paramValues = TreeMap<Int, Any?>()
        val paramTypes = TreeMap<Int, Int>()
        var adjustedQuery = query
        for (paramIndex in queryParams.keys) {
            val value = queryParams[paramIndex]
            if (value is Array) {
                val arrayProxy = value as JdbcArrayProxy
                val objects = arrayProxy.objects
                val arrayParamPos = ordinalIndexOf(adjustedQuery, '?', paramIndex)
                adjustedQuery = adjustedQuery.substring(0, arrayParamPos) +
                        makeArrayPlaceholders(objects.size) + adjustedQuery.substring(arrayParamPos + 1)
                for (i in objects.indices) {
                    val paramPos = paramIndex + arrayOffset + i
                    paramValues[paramPos] = objects[i]
                    paramTypes[paramPos] = arrayProxy.baseType
                }

                arrayOffset += objects.size - 1
            } else {
                paramValues[paramIndex + arrayOffset] = value!!
                paramTypes[paramIndex + arrayOffset] = queryTypes[paramIndex]!!
            }
        }


        var stmt: PreparedStatement? = null
        try {
            stmt = connection.prepareStatement(adjustedQuery)
            for (paramIndex in paramValues.keys) {
                val value = paramValues[paramIndex]
                when (paramTypes[paramIndex]) {
                    Types.INTEGER -> stmt!!.setInt(paramIndex, value as Int)

                    Types.BOOLEAN -> stmt!!.setBoolean(paramIndex, value as Boolean)

                    Types.VARCHAR, Types.LONGVARCHAR -> stmt!!.setString(paramIndex, value as String)

                    Types.BIGINT -> stmt!!.setLong(paramIndex, value as Long)

                    Types.FLOAT -> stmt!!.setFloat(paramIndex, value as Float)

                    ARR_PROXY_SET_OBJECT -> stmt!!.setObject(paramIndex, value)
                }

            }


        } catch (e: SQLException) {
            stmt?.close()

            throw e
        }

        return stmt
    }

    @Throws(SQLException::class)
    override fun executeQuery(): ResultSet {
        val stmt = prepareStatement()
        val resultSet = stmt!!.executeQuery()
        return PreparedStatementResultSetWrapper(resultSet, stmt)
    }


    private fun makeArrayPlaceholders(numPlaceholders: Int): String {
        val sb = StringBuffer(Math.max(0, 2 * numPlaceholders - 1))

        for (i in 0 until numPlaceholders) {
            if (i != 0)
                sb.append(',')

            sb.append('?')
        }

        return sb.toString()
    }

    @Throws(SQLException::class)
    override fun setNull(i: Int, i1: Int) {

    }

    @Throws(SQLException::class)
    override fun setBoolean(i: Int, b: Boolean) {
        queryParams[i] = b
        queryTypes[i] = Types.BOOLEAN
    }

    @Throws(SQLException::class)
    override fun setByte(i: Int, b: Byte) {
        queryParams[i] = b
        queryTypes[i] = Types.SMALLINT
    }

    @Throws(SQLException::class)
    override fun setShort(i: Int, i1: Short) {
        queryParams[i] = i1
        queryTypes[i] = Types.SMALLINT
    }

    @Throws(SQLException::class)
    override fun setInt(i: Int, i1: Int) {
        queryParams[i] = i1
        queryTypes[i] = Types.INTEGER
    }

    @Throws(SQLException::class)
    override fun setLong(i: Int, l: Long) {
        queryParams[i] = l
        queryTypes[i] = Types.BIGINT
    }

    @Throws(SQLException::class)
    override fun setFloat(i: Int, v: Float) {
        queryParams[i] = v
        queryTypes[i] = Types.FLOAT
    }

    @Throws(SQLException::class)
    override fun setDouble(i: Int, v: Double) {
        queryParams[i] = v
        queryTypes[i] = Types.DOUBLE
    }

    @Throws(SQLException::class)
    override fun setBigDecimal(i: Int, bigDecimal: BigDecimal) {
        throw SQLException("PreparedStatementArrayProxy unsupported type: BigDecimal")
    }

    @Throws(SQLException::class)
    override fun setString(i: Int, s: String) {
        queryParams[i] = s
        queryTypes[i] = Types.VARCHAR
    }

    @Throws(SQLException::class)
    override fun setBytes(i: Int, bytes: ByteArray) {
        throw SQLException("PreparedStatementArrayProxy unsupported type: Bytes")
    }

    @Throws(SQLException::class)
    override fun setDate(i: Int, date: Date) {
        throw SQLException("PreparedStatementArrayProxy unsupported type: Date")
    }

    @Throws(SQLException::class)
    override fun setTime(i: Int, time: Time) {

    }

    @Throws(SQLException::class)
    override fun setTimestamp(i: Int, timestamp: Timestamp) {

    }

    @Throws(SQLException::class)
    override fun setAsciiStream(i: Int, inputStream: InputStream, i1: Int) {

    }

    @Throws(SQLException::class)
    override fun setUnicodeStream(i: Int, inputStream: InputStream, i1: Int) {

    }

    @Throws(SQLException::class)
    override fun setBinaryStream(i: Int, inputStream: InputStream, i1: Int) {

    }

    @Throws(SQLException::class)
    override fun clearParameters() {

    }

    @Throws(SQLException::class)
    override fun setObject(i: Int, o: Any, i1: Int) {
        throw SQLException("Unsupported: setObject, Int")
    }

    @Throws(SQLException::class)
    override fun setObject(i: Int, o: Any) {
        queryParams[i] = o
        queryTypes[i] = ARR_PROXY_SET_OBJECT
    }

    @Throws(SQLException::class)
    override fun execute(): Boolean {
        try {
            prepareStatement()!!.use { stmt -> return stmt.execute() }
        } catch (e: SQLException) {
            throw e
        }

    }

    @Throws(SQLException::class)
    override fun addBatch() {

    }

    @Throws(SQLException::class)
    override fun setCharacterStream(i: Int, reader: Reader, i1: Int) {
        throw SQLException("PreparedStatementArrayProxy: Unsupported type: setCharacterStream")
    }

    @Throws(SQLException::class)
    override fun setRef(i: Int, ref: Ref) {
        throw SQLException("PreparedStatementArrayProxy: Unsupported type: setRef")
    }

    @Throws(SQLException::class)
    override fun setBlob(i: Int, blob: Blob) {
        throw SQLException("PreparedStatementArrayProxy: Unsupported type: blob")
    }

    @Throws(SQLException::class)
    override fun setClob(i: Int, clob: Clob) {

    }

    @Throws(SQLException::class)
    override fun getMetaData(): ResultSetMetaData? {
        return null
    }

    @Throws(SQLException::class)
    override fun setDate(i: Int, date: Date, calendar: Calendar) {

    }

    @Throws(SQLException::class)
    override fun setTime(i: Int, time: Time, calendar: Calendar) {

    }

    @Throws(SQLException::class)
    override fun setTimestamp(i: Int, timestamp: Timestamp, calendar: Calendar) {

    }

    @Throws(SQLException::class)
    override fun setNull(i: Int, i1: Int, s: String) {

    }

    @Throws(SQLException::class)
    override fun setURL(i: Int, url: URL) {

    }

    @Throws(SQLException::class)
    override fun getParameterMetaData(): ParameterMetaData? {
        return null
    }

    @Throws(SQLException::class)
    override fun setRowId(i: Int, rowId: RowId) {

    }

    @Throws(SQLException::class)
    override fun setNString(i: Int, s: String) {

    }

    @Throws(SQLException::class)
    override fun setNCharacterStream(i: Int, reader: Reader, l: Long) {

    }

    @Throws(SQLException::class)
    override fun setNClob(i: Int, nClob: NClob) {

    }

    @Throws(SQLException::class)
    override fun setClob(i: Int, reader: Reader, l: Long) {

    }

    @Throws(SQLException::class)
    override fun setBlob(i: Int, inputStream: InputStream, l: Long) {

    }

    @Throws(SQLException::class)
    override fun setNClob(i: Int, reader: Reader, l: Long) {

    }

    @Throws(SQLException::class)
    override fun setSQLXML(i: Int, sqlxml: SQLXML) {

    }

    @Throws(SQLException::class)
    override fun setObject(i: Int, o: Any, i1: Int, i2: Int) {

    }

    @Throws(SQLException::class)
    override fun setAsciiStream(i: Int, inputStream: InputStream, l: Long) {

    }

    @Throws(SQLException::class)
    override fun setBinaryStream(i: Int, inputStream: InputStream, l: Long) {

    }

    @Throws(SQLException::class)
    override fun setCharacterStream(i: Int, reader: Reader, l: Long) {

    }

    @Throws(SQLException::class)
    override fun setAsciiStream(i: Int, inputStream: InputStream) {

    }

    @Throws(SQLException::class)
    override fun setBinaryStream(i: Int, inputStream: InputStream) {

    }

    @Throws(SQLException::class)
    override fun setCharacterStream(i: Int, reader: Reader) {

    }

    @Throws(SQLException::class)
    override fun setNCharacterStream(i: Int, reader: Reader) {

    }

    @Throws(SQLException::class)
    override fun setClob(i: Int, reader: Reader) {

    }

    @Throws(SQLException::class)
    override fun setBlob(i: Int, inputStream: InputStream) {

    }

    @Throws(SQLException::class)
    override fun setNClob(i: Int, reader: Reader) {

    }

    @Throws(SQLException::class)
    override fun executeQuery(s: String): ResultSet? {
        return null
    }

    @Throws(SQLException::class)
    override fun executeUpdate(s: String): Int {
        return 0
    }

    @Throws(SQLException::class)
    override fun close() {

    }

    @Throws(SQLException::class)
    override fun getMaxFieldSize(): Int {
        return 0
    }

    @Throws(SQLException::class)
    override fun setMaxFieldSize(i: Int) {

    }

    @Throws(SQLException::class)
    override fun getMaxRows(): Int {
        return 0
    }

    @Throws(SQLException::class)
    override fun setMaxRows(i: Int) {

    }

    @Throws(SQLException::class)
    override fun setEscapeProcessing(b: Boolean) {

    }

    @Throws(SQLException::class)
    override fun getQueryTimeout(): Int {
        return 0
    }

    @Throws(SQLException::class)
    override fun setQueryTimeout(i: Int) {

    }

    @Throws(SQLException::class)
    override fun cancel() {

    }

    @Throws(SQLException::class)
    override fun getWarnings(): SQLWarning? {
        return null
    }

    @Throws(SQLException::class)
    override fun clearWarnings() {

    }

    @Throws(SQLException::class)
    override fun setCursorName(s: String) {

    }

    @Throws(SQLException::class)
    override fun execute(s: String): Boolean {
        return false
    }

    @Throws(SQLException::class)
    override fun getResultSet(): ResultSet? {
        return null
    }

    @Throws(SQLException::class)
    override fun getUpdateCount(): Int {
        return 0
    }

    @Throws(SQLException::class)
    override fun getMoreResults(): Boolean {
        return false
    }

    @Throws(SQLException::class)
    override fun setFetchDirection(i: Int) {

    }

    @Throws(SQLException::class)
    override fun getFetchDirection(): Int {
        return 0
    }

    @Throws(SQLException::class)
    override fun setFetchSize(i: Int) {

    }

    @Throws(SQLException::class)
    override fun getFetchSize(): Int {
        return 0
    }

    @Throws(SQLException::class)
    override fun getResultSetConcurrency(): Int {
        return 0
    }

    @Throws(SQLException::class)
    override fun getResultSetType(): Int {
        return 0
    }

    @Throws(SQLException::class)
    override fun addBatch(s: String) {

    }

    @Throws(SQLException::class)
    override fun clearBatch() {

    }

    @Throws(SQLException::class)
    override fun executeBatch(): IntArray {
        return IntArray(0)
    }

    @Throws(SQLException::class)
    override fun getConnection(): Connection {
        return connection
    }

    @Throws(SQLException::class)
    override fun getMoreResults(i: Int): Boolean {
        return false
    }

    @Throws(SQLException::class)
    override fun getGeneratedKeys(): ResultSet? {
        return null
    }

    @Throws(SQLException::class)
    override fun executeUpdate(s: String, i: Int): Int {
        return 0
    }

    @Throws(SQLException::class)
    override fun executeUpdate(s: String, ints: IntArray): Int {
        return 0
    }

    @Throws(SQLException::class)
    override fun executeUpdate(s: String, strings: kotlin.Array<String>): Int {
        return 0
    }

    @Throws(SQLException::class)
    override fun execute(s: String, i: Int): Boolean {
        return false
    }

    @Throws(SQLException::class)
    override fun execute(s: String, ints: IntArray): Boolean {
        return false
    }

    override fun execute(p0: String?, p1: kotlin.Array<out String>?) = false

    @Throws(SQLException::class)
    override fun getResultSetHoldability(): Int {
        return 0
    }

    @Throws(SQLException::class)
    override fun isClosed(): Boolean {
        return false
    }

    @Throws(SQLException::class)
    override fun setPoolable(b: Boolean) {

    }

    @Throws(SQLException::class)
    override fun isPoolable(): Boolean {
        return false
    }

    @Throws(SQLException::class)
    override fun closeOnCompletion() {

    }

    @Throws(SQLException::class)
    override fun isCloseOnCompletion(): Boolean {
        return false
    }

    @Throws(SQLException::class)
    override fun <T> unwrap(aClass: Class<T>): T? {
        return null
    }

    @Throws(SQLException::class)
    override fun isWrapperFor(aClass: Class<*>): Boolean {
        return false
    }

    @Throws(SQLException::class)
    override fun setArray(i: Int, array: Array) {
        queryParams[i] = array
        queryTypes[i] = Types.ARRAY
    }


    @Throws(SQLException::class)
    override fun executeUpdate(): Int {
        try {
            prepareStatement()!!.use { stmt -> return stmt.executeUpdate() }
        } catch (e: SQLException) {
            throw e
        }

    }

    companion object {

        const val ARR_PROXY_SET_OBJECT = -5000

        /**
         * Create a proxy array, using the same method parameters as a JDBC connection uses to create it.
         *
         * @param arrayType the JDBC data type e.g. "VARCHAR", "INTEGER", etc
         * @param objects The objects contained in the array
         *
         * @return A java.sql.Array object that can be used as a parameter with this class
         */
        @JvmStatic
        fun createArrayOf(arrayType: String, objects: kotlin.Array<out Any?>): Array {
            return JdbcArrayProxy(arrayType, objects)
        }

        private fun ordinalIndexOf(str: String, c: Char, n: Int): Int {
            var n = n
            var pos = str.indexOf(c)
            while (--n > 0 && pos != -1)
                pos = str.indexOf(c, pos + 1)

            return pos
        }
    }
}
