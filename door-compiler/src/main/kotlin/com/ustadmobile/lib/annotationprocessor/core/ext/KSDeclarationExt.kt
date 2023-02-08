package com.ustadmobile.lib.annotationprocessor.core.ext

import com.ustadmobile.door.lifecycle.LiveData
import com.google.devtools.ksp.symbol.*
import com.ustadmobile.door.paging.DataSourceFactory
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

fun KSDeclaration.isDataSourceFactory(): Boolean {
    return (this as? KSClassDeclaration)?.qualifiedName?.asString() == DataSourceFactory::class.qualifiedName
}

fun KSDeclaration.isPagingSource(): Boolean {
    return(this as? KSClassDeclaration)?.qualifiedName?.asString() == PagingSource::class.qualifiedName
}

fun KSDeclaration.isLiveData(): Boolean {
    return (this as? KSClassDeclaration)?.qualifiedName?.asString() == LiveData::class.qualifiedName
}

fun KSDeclaration.isFlow(): Boolean {
    return (this as? KSClassDeclaration)?.qualifiedName?.asString() == Flow::class.qualifiedName
}

fun KSDeclaration.isPagingSourceOrDataSourceFactoryOrLiveDataOrFlow(

) =  isDataSourceFactory() || isPagingSource() || isLiveData() || isFlow()

