package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.ustadmobile.door.DoorDbType

fun KSType.unwrapComponentTypeIfListOrArray(
    resolver: Resolver
): KSType {
    return if(isListOrArrayType(resolver)) {
        this.arguments.first().type?.resolve()
            ?: throw IllegalArgumentException("unwrapComponentTypeIfListOrArray: List or array type cannot be resolved!")
    }else {
        this
    }

}

fun KSType.isListOrArrayType(
    resolver: Resolver
): Boolean {
    return (this == resolver.builtIns.arrayType)
            || ((this.declaration as? KSClassDeclaration)?.isListDeclaration() == true)
}

fun KSType.resolveIfAlias(): KSType {
    return if(this is KSTypeAlias) {
        findActualType()
    }else {
        this
    }
}

private fun Resolver.sqliteIntegerTypes(): List<KSType> {
    return listOf(builtIns.booleanType, builtIns.byteType, builtIns.shortType, builtIns.intType, builtIns.longType)
        .flatMap { listOf(it, it.makeNullable()) }
}

private fun Resolver.sqliteRealTypes(): List<KSType> {
    return listOf(builtIns.floatType, builtIns.doubleType).flatMap { listOf(it, it.makeNullable()) }
}

fun KSType.toSqlType(
    dbType: Int,
    resolver: Resolver,
): String {
    fun KSType.equalsIgnoreNullable(otherType: KSType) : Boolean {
        return this == otherType || this == otherType.makeNullable()
    }

    val builtIns = resolver.builtIns
    return when {
        this.equalsIgnoreNullable(builtIns.stringType) -> "TEXT"
        dbType == DoorDbType.SQLITE && this in resolver.sqliteIntegerTypes() -> "INTEGER"
        dbType == DoorDbType.SQLITE && this in resolver.sqliteRealTypes() -> "REAl"
        this.equalsIgnoreNullable(builtIns.booleanType) -> "BOOL"
        this.equalsIgnoreNullable(builtIns.byteType) -> "SMALLINT"
        this.equalsIgnoreNullable(builtIns.shortType) -> "SMALLINT"
        this.equalsIgnoreNullable(builtIns.intType) -> "INTEGER"
        this.equalsIgnoreNullable(builtIns.longType) -> "BIGINT"
        this.equalsIgnoreNullable(builtIns.floatType) -> "FLOAT"
        this.equalsIgnoreNullable(builtIns.doubleType) -> "DOUBLE PRECISION"
        else -> "INVALID UNSUPPORTED TYPE"
    }
}
