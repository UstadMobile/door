package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.symbol.*
import com.ustadmobile.door.paging.PagingSource
import kotlinx.coroutines.flow.Flow

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

fun KSDeclaration.isPagingSource(): Boolean {
    return(this as? KSClassDeclaration)?.qualifiedName?.asString() == PagingSource::class.qualifiedName
}

fun KSDeclaration.isFlow(): Boolean {
    return (this as? KSClassDeclaration)?.qualifiedName?.asString() == Flow::class.qualifiedName
}

fun KSDeclaration.isPagingSourceOrFlow() =  isPagingSource() ||  isFlow()

