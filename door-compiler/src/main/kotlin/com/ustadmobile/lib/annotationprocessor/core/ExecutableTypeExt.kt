package com.ustadmobile.lib.annotationprocessor.core

import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType

/**
 * Figures out the return type of a method (even if it is a suspended method).
 */
val ExecutableType.suspendedSafeReturnType : TypeName
    get()  {
        val continuationParam = parameterTypes.firstOrNull { isContinuationParam(it.asTypeName()) }
        return if(continuationParam != null) {
            //The continuation parameter is always the last parameter, and has one type argument
            val contReturnType = (parameterTypes.last() as DeclaredType).typeArguments.first().extendsBoundOrSelf().asTypeName()
            removeTypeProjection(contReturnType)
            //Open classes can result in <out T> being generated instead of just <T>. Therefor we want to remove the wildcard
        }else {
            returnType.asTypeName().javaToKotlinType()
        }
    }