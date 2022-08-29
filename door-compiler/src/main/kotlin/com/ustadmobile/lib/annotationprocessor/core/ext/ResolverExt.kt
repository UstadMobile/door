package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

fun Resolver.sqlNumericNonNullTypes(): List<KSType> {
    return listOf(builtIns.byteType, builtIns.shortType, builtIns.intType, builtIns.longType, builtIns.floatType,
        builtIns.doubleType)
}

fun Resolver.querySingularTypes(): List<KSType> {
    return listOf(builtIns.booleanType, builtIns.byteType, builtIns.shortType, builtIns.intType, builtIns.longType,
        builtIns.floatType, builtIns.doubleType, builtIns.stringType).flatMap { listOf(it, it.makeNullable()) }
}


fun Resolver.getDatabaseSymbolsToProcess(): Sequence<KSClassDeclaration>{
    return getSymbolsWithAnnotation("com.ustadmobile.door.annotation.DoorDatabase")
        .filterIsInstance<KSClassDeclaration>()
        .filter { Modifier.ACTUAL !in it.modifiers }
}

fun Resolver.getDaoSymbolsToProcess(): Sequence<KSClassDeclaration> {
    return getSymbolsWithAnnotation("com.ustadmobile.door.annotation.DoorDao")
        .filterIsInstance<KSClassDeclaration>()
        .filter { Modifier.ACTUAL !in it.modifiers }
}
