package com.ustadmobile.door
import com.ustadmobile.door.jdbc.*
import com.ustadmobile.door.jdbc.types.*
import com.ustadmobile.door.ext.useStatement
import kotlin.jvm.JvmStatic
import kotlin.math.max

abstract class PreparedStatementArrayProxyCommon(
    protected val query: String,
    protected val connectionInternal: Connection
) : PreparedStatement {

    protected val queryParams = mutableMapOf<Int, Any?>()

    protected val queryTypes = mutableMapOf<Int, Int>()

    @Throws(SQLException::class)
    override fun setBoolean(i: Int, b: Boolean) {
        queryParams[i] = b
        queryTypes[i] = TypesKmp.BOOLEAN
    }

    @Throws(SQLException::class)
    override fun setByte(i: Int, b: Byte) {
        queryParams[i] = b
        queryTypes[i] = TypesKmp.SMALLINT
    }

    @Throws(SQLException::class)
    override fun setShort(i: Int, i1: Short) {
        queryParams[i] = i1
        queryTypes[i] = TypesKmp.SMALLINT
    }

    @Throws(SQLException::class)
    override fun setInt(i: Int, i1: Int) {
        queryParams[i] = i1
        queryTypes[i] = TypesKmp.INTEGER
    }

    @Throws(SQLException::class)
    override fun setLong(i: Int, l: Long) {
        queryParams[i] = l
        queryTypes[i] = TypesKmp.BIGINT
    }

    @Throws(SQLException::class)
    override fun setFloat(i: Int, v: Float) {
        queryParams[i] = v
        queryTypes[i] = TypesKmp.FLOAT
    }

    @Throws(SQLException::class)
    override fun setDouble(i: Int, v: Double) {
        queryParams[i] = v
        queryTypes[i] = TypesKmp.DOUBLE
    }

    @Throws(SQLException::class)
    override fun setString(i: Int, s: String?) {
        queryParams[i] = s
        queryTypes[i] = TypesKmp.VARCHAR
    }

    @Throws(SQLException::class)
    override fun setArray(i: Int, array: com.ustadmobile.door.jdbc.Array) {
        queryParams[i] = array
        queryTypes[i] = TypesKmp.ARRAY
    }

    @Throws(SQLException::class)
    override fun setBigDecimal(i: Int, bigDecimal: BigDecimal) {
        throw SQLException("PreparedStatementArrayProxy unsupported type: BigDecimal")
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
    override fun setObject(i: Int, o: Any?) {
        queryParams[i] = o
        queryTypes[i] = ARR_PROXY_SET_OBJECT
    }



    protected fun prepareStatement(): PreparedStatement {
        var arrayOffset = 0
        val paramValues = mutableMapOf<Int, Any?>()
        val paramTypes = mutableMapOf<Int, Int>()
        var adjustedQuery = query
        for (paramIndex in queryParams.keys) {
            val value = queryParams[paramIndex]
            if (value is com.ustadmobile.door.jdbc.Array) {
                val arrayProxy = value as JdbcArrayProxy
                val objects = arrayProxy.objects
                val arrayParamPos = ordinalIndexOf(adjustedQuery, '?', paramIndex)
                adjustedQuery = adjustedQuery.substring(0, arrayParamPos) +
                        makeArrayPlaceholders(objects.size) + adjustedQuery.substring(arrayParamPos + 1)
                for (i in objects.indices) {
                    val paramPos = paramIndex + arrayOffset + i
                    paramValues[paramPos] = objects[i]
                    paramTypes[paramPos] = arrayProxy.getBaseType()
                }

                arrayOffset += objects.size - 1
            } else {
                paramValues[paramIndex + arrayOffset] = value!!
                paramTypes[paramIndex + arrayOffset] = queryTypes[paramIndex]!!
            }
        }


        var stmt: PreparedStatement? = null
        try {
            stmt = connectionInternal.prepareStatement(adjustedQuery)
            for (paramIndex in paramValues.keys) {
                val value = paramValues[paramIndex]
                when (paramTypes[paramIndex]) {
                    TypesKmp.INTEGER -> stmt!!.setInt(paramIndex, value as Int)

                    TypesKmp.BOOLEAN -> stmt!!.setBoolean(paramIndex, value as Boolean)

                    TypesKmp.VARCHAR, TypesKmp.LONGVARCHAR -> stmt!!.setString(paramIndex, value as String)

                    TypesKmp.BIGINT -> stmt!!.setLong(paramIndex, value as Long)

                    TypesKmp.FLOAT -> stmt!!.setFloat(paramIndex, value as Float)

                    ARR_PROXY_SET_OBJECT -> stmt!!.setObject(paramIndex, value)
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
        val resultSet = stmt!!.executeQuery()
        return PreparedStatementResultSetWrapper(resultSet, stmt)
    }


    @Throws(SQLException::class)
    override fun executeUpdate(): Int {
        return prepareStatement().useStatement { stmt ->
            stmt.executeUpdate()
        }
    }



    companion object {
        fun ordinalIndexOf(str: String, c: Char, n: Int): Int {
            var n = n
            var pos = str.indexOf(c)
            while (--n > 0 && pos != -1)
                pos = str.indexOf(c, pos + 1)

            return pos
        }

        const val ARR_PROXY_SET_OBJECT = -5000

    }



}