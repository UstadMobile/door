package com.ustadmobile.door.httpsql

import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.TypesKmp
import kotlinx.serialization.Serializable

@Serializable
class PreparedStatementParam(
    val index: Int,
    val value: List<String?>,
    val sqlType: Int,
)

fun PreparedStatement.setPreparedStatementParam(param: PreparedStatementParam) {
    val paramVal = param.value.firstOrNull()

    if(paramVal == null) {
        setNull(param.index, param.sqlType)
        return
    }

    when(param.sqlType) {
        TypesKmp.BOOLEAN -> setBoolean(param.index, paramVal.toBoolean())
        TypesKmp.TINYINT -> setByte(param.index, paramVal.toByte())
        TypesKmp.SMALLINT -> setShort(param.index, paramVal.toShort())
        TypesKmp.INTEGER -> setInt(param.index, paramVal.toInt())
        TypesKmp.BIGINT -> setLong(param.index, paramVal.toLong())
        TypesKmp.FLOAT -> setFloat(param.index, paramVal.toFloat())
        TypesKmp.DOUBLE -> setDouble(param.index, paramVal.toDouble())
        TypesKmp.LONGVARCHAR -> setString(param.index, paramVal)
    }

}

