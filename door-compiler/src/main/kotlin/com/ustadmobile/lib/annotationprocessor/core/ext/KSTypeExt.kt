package com.ustadmobile.lib.annotationprocessor.core.ext

import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.CodeBlock
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

fun KSType.unwrapLiveDataOrDataSourceFactoryResultType(
    resolver: Resolver,
): KSType {
    val qualifiedName = this.declaration.qualifiedName?.asString()
    if (qualifiedName == LiveData::class.qualifiedName) {
        return this.arguments.first().type?.resolve()
            ?: throw IllegalArgumentException("unwrapLiveDataOrDataSourceFactoryResultType: Cannot resolve LiveData type!")
    } else if (qualifiedName == DataSource.Factory::class.qualifiedName) {
        val entityTypeRef = resolver.createKSTypeReferenceFromKSType(this)
        return resolver.getClassDeclarationByName(resolver.getKSNameFromString("kotlin.collections.List"))
            ?.asType(listOf(resolver.getTypeArgument(entityTypeRef, Variance.INVARIANT)))
                ?: throw IllegalArgumentException("unwrapLiveDataOrDataSourceFactoryResultType: could not lookup datasource comp type")
    }else {
        return this
    }

}


fun KSType.isList(): Boolean {
    return (declaration as? KSClassDeclaration)?.isListDeclaration() == true
}

fun KSType.isListOrArrayType(
    resolver: Resolver
): Boolean {
    return (this == resolver.builtIns.arrayType) || isList()
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

private fun KSType.equalsIgnoreNullable(otherType: KSType) : Boolean {
    return this == otherType || this == otherType.makeNullable()
}

fun KSType.toSqlType(
    dbType: Int,
    resolver: Resolver,
): String {
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

fun KSType.defaultTypeValueCode(
    resolver: Resolver
): CodeBlock {
    val builtIns = resolver.builtIns
    val that = this
    return CodeBlock.builder()
        .apply {
            when {
                isMarkedNullable -> add("null")
                that == builtIns.intType -> add("0")
                that == builtIns.longType -> add("0L")
                that == builtIns.byteType -> add("0.toByte()")
                that == builtIns.floatType -> add("0.toFloat()")
                that == builtIns.doubleType -> add("0.toDouble()")
                that == builtIns.booleanType -> add("false")
                that == builtIns.stringType -> add("\"\"")
                (that.declaration as? KSClassDeclaration)?.isListDeclaration() == true -> {
                    add("mutableListOf()")
                }
                (that.declaration as? KSClassDeclaration)?.isListDeclaration() == true -> {
                    add("emptyList()")
                }
            }
        }
        .build()
}

fun KSType.defaultSqlQueryVal(resolver: Resolver) : String {
    return when {
        this in resolver.sqlNumericNonNullTypes() -> "0"
        this == resolver.builtIns.booleanType  -> "false"
        this == resolver.builtIns.stringType -> "''"
        else -> "null"
    }
}


fun KSType.preparedStatementSetterGetterTypeName(
    resolver: Resolver
): String {
    val builtIns = resolver.builtIns
    return when {
        this.equalsIgnoreNullable(builtIns.intType) -> "Int"
        this.equalsIgnoreNullable(builtIns.byteType) -> "Byte"
        this.equalsIgnoreNullable(builtIns.longType) -> "Long"
        this.equalsIgnoreNullable(builtIns.floatType) -> "Float"
        this.equalsIgnoreNullable(builtIns.doubleType) -> "Double"
        this.equalsIgnoreNullable(builtIns.booleanType) -> "Boolean"
        this.equalsIgnoreNullable(builtIns.stringType) -> "String"
        this == builtIns.arrayType -> "Array"
        (this.declaration as? KSClassDeclaration)?.isListDeclaration() == true -> "Array"
        else -> "ERR_UNKNOWN_TYPE"
    }
}

fun KSType.isArrayType(): Boolean {
    return (declaration as? KSClassDeclaration)?.isArrayDeclaration() == true
}

fun KSType.isLongArray(): Boolean {
    return (declaration as? KSClassDeclaration)?.qualifiedName?.asString() == "kotlin.LongArray"
}

fun KSType.isIntArray(): Boolean {
    return (declaration as? KSClassDeclaration)?.qualifiedName?.asString() == "kotlin.IntArray"
}

fun KSType.isDataSourceFactory() = declaration.isDataSourceFactory()

fun KSType.isLiveData() = declaration.isLiveData()

fun KSType.isDataSourceFactoryOrLiveData() = declaration.isDataSourceFactoryOrLiveData()

