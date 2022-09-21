package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.ustadmobile.door.ext.createArrayOrProxyArrayOf
import java.sql.PreparedStatement

fun PreparedStatement.setDefaultParamValue(
    resolver: Resolver,
    index: Int,
    paramName: String,
    paramType: KSType,
    dbType: Int
) {
    val builtIns = resolver.builtIns
    when {
        paramType.isMarkedNullable -> setNull(index, paramType.toSqlTypeInt(dbType, resolver))
        paramType == builtIns.booleanType -> setBoolean(index, false)
        paramType == builtIns.byteType -> setByte(index, 0)
        paramType == builtIns.shortType -> setShort(index, 0)
        paramType == builtIns.intType -> setInt(index, 0)
        paramType == builtIns.longType -> setLong(index, 0L)
        paramType == builtIns.floatType -> setFloat(index, 0.toFloat())
        paramType == builtIns.doubleType -> setDouble(index, 0.toDouble())
        paramType == builtIns.stringType -> setString(index, "")
        paramType.isListOrArrayType(resolver) -> {
            val componentType = paramType.unwrapComponentTypeIfListOrArray(resolver)
            setArray(index, connection.createArrayOrProxyArrayOf(componentType.toSqlType(dbType, resolver),
                arrayOf()))
        }
        else -> throw com.ustadmobile.door.jdbc.SQLException("$paramName Unsupported type: $paramType")
    }
}
