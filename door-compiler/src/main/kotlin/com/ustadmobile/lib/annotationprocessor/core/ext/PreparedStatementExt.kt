package com.ustadmobile.lib.annotationprocessor.core.ext

import com.ustadmobile.door.ext.createArrayOf
import com.ustadmobile.lib.annotationprocessor.core.toSqlType
import com.ustadmobile.lib.annotationprocessor.core.unwrapListOrArrayComponentType
import java.sql.PreparedStatement
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

fun PreparedStatement.setDefaultParamValue(
    processingEnv: ProcessingEnvironment,
    index: Int,
    paramName: String,
    paramType: TypeMirror,
    dbType: Int
){
    when(paramType.kind) {
        TypeKind.BOOLEAN -> setBoolean(index, false)
        TypeKind.BYTE -> setByte(index, 0)
        TypeKind.SHORT -> setShort(index, 0)
        TypeKind.INT -> setInt(index, 0)
        TypeKind.LONG -> setLong(index, 0)
        TypeKind.FLOAT -> setFloat(index, 0.toFloat())
        TypeKind.DOUBLE -> setDouble(index, 0.toDouble())
        TypeKind.DECLARED -> {
            val declaredType = paramType as DeclaredType
            if(declaredType.asElement() == processingEnv.elementUtils.getTypeElement("java.lang.String")) {
                setString(index, null)
            }else if(declaredType.asElement() == processingEnv.elementUtils.getTypeElement("java.util.List")) {
                val componentType = declaredType.unwrapListOrArrayComponentType(processingEnv)
                setArray(index, connection.createArrayOf(dbType, componentType.toSqlType(dbType, processingEnv),
                    arrayOf()))
            }

            else {
                throw com.ustadmobile.door.jdbc.SQLException("$paramName Unsupported type: $paramType")
            }
        }
        else -> throw com.ustadmobile.door.jdbc.SQLException("$paramName Unsupported type: $paramType")
    }
}