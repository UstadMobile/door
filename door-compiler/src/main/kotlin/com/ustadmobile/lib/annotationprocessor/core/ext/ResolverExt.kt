package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType

fun Resolver.sqlNumericNonNullTypes(): List<KSType> {
    return listOf(builtIns.byteType, builtIns.shortType, builtIns.intType, builtIns.longType, builtIns.floatType,
        builtIns.doubleType)
}

fun Resolver.querySingularTypes(): List<KSType> {
    return listOf(builtIns.booleanType, builtIns.byteType, builtIns.shortType, builtIns.intType, builtIns.longType,
        builtIns.floatType, builtIns.doubleType, builtIns.stringType).flatMap { listOf(it, it.makeNullable()) }
}

