package com.ustadmobile.door

import com.ustadmobile.door.jdbc.TypesKmp
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
 * Create a new PreparedStatementArrayProxy
 *
 * @param query The query to execute (as per a normal PreparedStatement using ? for parameters)
 * @param connection the JDBC connection to run the query on
 *
 */
actual class PreparedStatementArrayProxy actual constructor(
    query: String,
    connection: Connection
) : PreparedStatementArrayProxyCommon(query, connection) {

    @Throws(SQLException::class)
    override fun setNull(i: Int, i1: Int) {

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
    override fun setTimestamp(i: Int, timestamp: Timestamp) {

    }


    @Throws(SQLException::class)
    override fun setObject(i: Int, o: Any, i1: Int) {
        throw SQLException("Unsupported: setObject, Int")
    }


    @Throws(SQLException::class)
    override fun clearParameters() {

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
        return connectionInternal
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


}
