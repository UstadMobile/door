package com.ustadmobile.lib.annotationprocessor.core.ext

import androidx.lifecycle.LiveData
import com.google.devtools.ksp.symbol.*
import androidx.paging.DataSource

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

fun KSDeclaration.isDataSourceFactory(): Boolean {
    return (this as? KSClassDeclaration)?.qualifiedName?.asString() == DataSource.Factory::class.qualifiedName
}

fun KSDeclaration.isLiveData(): Boolean {
    return (this as? KSClassDeclaration)?.qualifiedName?.asString() == LiveData::class.qualifiedName
}

fun KSDeclaration.isDataSourceFactoryOrLiveData() =  isDataSourceFactory() || isLiveData()

