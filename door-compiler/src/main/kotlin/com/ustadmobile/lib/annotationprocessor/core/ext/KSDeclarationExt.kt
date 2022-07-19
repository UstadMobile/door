package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.symbol.*

fun KSDeclaration.propertyOrReturnType(): KSTypeReference? {
    return when(this) {
        is KSPropertyDeclaration -> type
        is KSFunctionDeclaration -> returnType
        else -> throw IllegalArgumentException("propertyOrReturnType: ${this.simpleName.asString()} is not a function or property!")
    }
}

fun KSDeclaration.toPropertyOrEmptyFunctionCaller(): String {
    var accessor = simpleName.asString()
    if(this is KSFunctionDeclaration)
        accessor += "()"

    return accessor
}

