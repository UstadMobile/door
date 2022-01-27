package com.ustadmobile.door
import com.ustadmobile.door.jdbc.*
import com.ustadmobile.door.jdbc.types.*
import com.ustadmobile.door.ext.useStatement
import kotlin.math.max

abstract class PreparedStatementArrayProxyCommon(
    protected val query: String,
    protected val connectionInternal: Connection
) : PreparedStatement {

    private val queryParams = mutableMapOf<Int, Any?>()

    private val queryTypes = mutableMapOf<Int, Int>()

    protected var stmtQueryTimeout: Int = -1

    /**
     * Get the index of the nth occurence of a character within a string
     *
     * @receiver a string in which to search for a given character
     * @param char the character to search for
     * @param n the occurence index to look for (starting from 1 NOT 0,
     * because we are working with JDBC parameters indexes)
     *
     * @return the index of the nth occurrence of a character
     */
    private fun String.getNthIndexOf(char: Char, n: Int) : Int{
        var foundCount = 0
        var pos = 0
        while(foundCount++ < n && pos != -1) {
            pos = this.indexOf(char, pos + 1)
        }

        return pos
    }

    @Throws(SQLException::class)
    override fun setBoolean(index: Int, value: Boolean) {
        queryParams[index] = value
        queryTypes[index] = TypesKmp.BOOLEAN
    }

    @Throws(SQLException::class)
    override fun setByte(index: Int, value: Byte) {
        queryParams[index] = value
        queryTypes[index] = TypesKmp.SMALLINT
    }

    @Throws(SQLException::class)
    override fun setShort(index: Int, value: Short) {
        queryParams[index] = value
        queryTypes[index] = TypesKmp.SMALLINT
    }

    @Throws(SQLException::class)
    override fun setInt(index: Int, value: Int) {
        queryParams[index] = value
        queryTypes[index] = TypesKmp.INTEGER
    }

    @Throws(SQLException::class)
    override fun setLong(index: Int, value: Long) {
        queryParams[index] = value
        queryTypes[index] = TypesKmp.BIGINT
    }

    @Throws(SQLException::class)
    override fun setFloat(index: Int, value: Float) {
        queryParams[index] = value
        queryTypes[index] = TypesKmp.FLOAT
    }

    @Throws(SQLException::class)
    override fun setDouble(index: Int, value: Double) {
        queryParams[index] = value
        queryTypes[index] = TypesKmp.DOUBLE
    }

    @Throws(SQLException::class)
    override fun setString(index: Int, value: String?) {
        queryParams[index] = value
        queryTypes[index] = TypesKmp.VARCHAR
    }

    @Suppress("RemoveRedundantQualifierName")
    @Throws(SQLException::class)
    override fun setArray(index: Int, array: com.ustadmobile.door.jdbc.Array) {
        queryParams[index] = array
        queryTypes[index] = TypesKmp.ARRAY
    }

    @Throws(SQLException::class)
    override fun setBigDecimal(index: Int, value: BigDecimal) {
        throw SQLException("PreparedStatementArrayProxy unsupported type: BigDecimal")
    }

    @Throws(SQLException::class)
    override fun setBytes(index: Int, value: ByteArray) {
        throw SQLException("PreparedStatementArrayProxy unsupported type: Bytes")
    }

    @Throws(SQLException::class)
    override fun setDate(index: Int, value: Date) {
        throw SQLException("PreparedStatementArrayProxy unsupported type: Date")
    }


    @Throws(SQLException::class)
    override fun setTime(index: Int, value: Time) {

    }

    @Throws(SQLException::class)
    override fun setObject(index: Int, value: Any?) {
        queryParams[index] = value
        queryTypes[index] = ARR_PROXY_SET_OBJECT
    }

    override fun setQueryTimeout(seconds: Int) {
        stmtQueryTimeout = seconds
    }

    @Suppress("RemoveRedundantQualifierName")
    internal fun prepareStatement(): PreparedStatement {
        var arrayOffset = 0
        val paramValues = mutableMapOf<Int, Any?>()
        val paramTypes = mutableMapOf<Int, Int>()
        var adjustedQuery = query
        for (paramIndex in queryParams.keys) {
            val value = queryParams[paramIndex]
            if (value is com.ustadmobile.door.jdbc.Array) {
                val arrayProxy = value as JdbcArrayProxy
                val objects = arrayProxy.objects
                val arrayParamPos = adjustedQuery.getNthIndexOf('?', paramIndex + arrayOffset)
                adjustedQuery = adjustedQuery.substring(0, arrayParamPos) +
                        makeArrayPlaceholders(objects.size) + adjustedQuery.substring(arrayParamPos + 1)
                for (i in objects.indices) {
                    val paramPos = paramIndex + arrayOffset + i
                    paramValues[paramPos] = objects[i]
                    paramTypes[paramPos] = arrayProxy.getBaseType()
                }

                arrayOffset += objects.size - 1
            } else {
                paramValues[paramIndex + arrayOffset] = value
                paramTypes[paramIndex + arrayOffset] = queryTypes[paramIndex] ?: throw IllegalStateException(
                    "PreparedStatementArrayProxy: Cannot find query param type at index $paramIndex")
            }
        }


        var stmt: PreparedStatement? = null
        try {
            stmt = connectionInternal.prepareStatement(adjustedQuery)
            stmt.takeIf { stmtQueryTimeout > 0 }?.setQueryTimeout(stmtQueryTimeout)
            for (paramIndex in paramValues.keys) {
                val value = paramValues[paramIndex]
                when (paramTypes[paramIndex]) {
                    TypesKmp.INTEGER -> stmt.setInt(paramIndex, value as Int)

                    TypesKmp.BOOLEAN -> stmt.setBoolean(paramIndex, value as Boolean)

                    TypesKmp.VARCHAR, TypesKmp.LONGVARCHAR -> stmt.setString(paramIndex, value as String?)

                    TypesKmp.BIGINT -> stmt.setLong(paramIndex, value as Long)

                    TypesKmp.FLOAT -> stmt.setFloat(paramIndex, value as Float)

                    ARR_PROXY_SET_OBJECT -> stmt.setObject(paramIndex, value)
                }

            }


        } catch (e: SQLException) {
            stmt?.close()

            throw e
        }

        return stmt
    }

    private fun makeArrayPlaceholders(numPlaceholders: Int): String {
        val sb = StringBuilder(max(0, 2 * numPlaceholders - 1))

        for (i in 0 until numPlaceholders) {
            if (i != 0)
                sb.append(',')

            sb.append('?')
        }

        return sb.toString()
    }

    override fun executeQuery(): ResultSet {
        val stmt = prepareStatement()
        val resultSet = stmt.executeQuery()
        return PreparedStatementResultSetWrapper(resultSet, stmt)
    }


    @Throws(SQLException::class)
    override fun executeUpdate(): Int {
        return prepareStatement().useStatement { stmt ->
            stmt.executeUpdate()
        }
    }



    companion object {

        const val ARR_PROXY_SET_OBJECT = -5000

    }



}