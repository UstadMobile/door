package com.ustadmobile.lib.annotationprocessor.core.ext

import app.cash.paging.PagingSource
import com.google.devtools.ksp.findActualType
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.CodeBlock
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.jdbc.TypesKmp
import kotlinx.coroutines.flow.Flow

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

/**
 *
 * @receiver The return type of a DAO function. This can be a primitive, object, a list or array of a primitive or object
 * or Flow with a primitive or object type reference
 * e.g. Entity, List<Entity>, Flow<Entity>, Flow<List<Entity>>, PagingSource<Int, Entity> etc.
 *
 * @param resolver the KSP resolver
 *
 * @return The result type expected when running the actual query - either the object itself, or a list of objects. This
 * will unwrap Flow and PagingSource
 * e.g. Entity, List<Entity>, Entity, List<Entity> etc.
 *
 */
fun KSType.unwrapResultType(
    resolver: Resolver,
): KSType {
    val qualifiedName = this.resolveActualTypeIfAliased().declaration.qualifiedName?.asString()
    return when (qualifiedName) {
        PagingSource::class.qualifiedName -> {
            val entityTypeRef = arguments.get(1).type ?: throw IllegalArgumentException("PagingSource has no type argument")
            resolver.getClassDeclarationByName(resolver.getKSNameFromString("kotlin.collections.List"))
                ?.asType(listOf(resolver.getTypeArgument(entityTypeRef, Variance.INVARIANT)))
                ?: throw IllegalArgumentException("unwrapResultType: could not lookup pagingsource comp type")
        }
        Flow::class.qualifiedName -> {
            this.arguments.first().type?.resolve()
                ?: throw IllegalArgumentException("unwrapResultType: Cannot resolve Flow type!")
        }
        else -> {
            this
        }
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

private fun Resolver.sqliteIntegerTypes(): List<KSType> {
    return listOf(builtIns.booleanType, builtIns.byteType, builtIns.shortType, builtIns.intType, builtIns.longType)
        .flatMap { listOf(it, it.makeNullable()) }
}

private fun Resolver.sqliteRealTypes(): List<KSType> {
    return listOf(builtIns.floatType, builtIns.doubleType).flatMap { listOf(it, it.makeNullable()) }
}

fun KSType.equalsIgnoreNullable(
    other: KSType,
    ignoreNullability: Boolean = true
): Boolean {
    return if(ignoreNullability) {
        this.makeNotNullable() == other.makeNotNullable()
    }else {
        this == other
    }
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

fun KSType.toSqlTypeInt(
    dbType: Int,
    resolver: Resolver
): Int {
    val builtIns = resolver.builtIns
    return when {
        this.equalsIgnoreNullable(builtIns.stringType) -> TypesKmp.LONGVARCHAR
        dbType == DoorDbType.SQLITE && this in resolver.sqliteIntegerTypes() -> TypesKmp.INTEGER
        dbType == DoorDbType.SQLITE && this in resolver.sqliteRealTypes() -> TypesKmp.REAL
        this.equalsIgnoreNullable(builtIns.booleanType) -> TypesKmp.BOOLEAN
        this.equalsIgnoreNullable(builtIns.byteType) -> TypesKmp.SMALLINT
        this.equalsIgnoreNullable(builtIns.shortType) -> TypesKmp.SMALLINT
        this.equalsIgnoreNullable(builtIns.intType) -> TypesKmp.INTEGER
        this.equalsIgnoreNullable(builtIns.longType) -> TypesKmp.BIGINT
        this.equalsIgnoreNullable(builtIns.floatType) -> TypesKmp.FLOAT
        this.equalsIgnoreNullable(builtIns.doubleType) -> TypesKmp.DOUBLE
        else -> 0
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

fun KSType.defaultSqlQueryVal(
    resolver: Resolver,
    dbProductType: Int,
) : String {
    return when {
        this in resolver.sqlNumericNonNullTypes() -> "0"
        dbProductType == DoorDbType.SQLITE && this == resolver.builtIns.booleanType -> "0"
        this == resolver.builtIns.booleanType  -> "false"
        this == resolver.builtIns.stringType -> "''"
        else -> "null"
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

fun KSType.isPagingSource(): Boolean = resolveActualTypeIfAliased().declaration.isPagingSource()

fun KSType.isPagingSourceOrFlow() = resolveActualTypeIfAliased().declaration.isPagingSourceOrFlow()

fun KSType.isFlow() = declaration.isFlow()

fun KSType.resolveActualTypeIfAliased(): KSType {
    return if(declaration is KSTypeAlias) {
        (declaration as KSTypeAlias).findActualType().asType(arguments)
    }else {
        this
    }
}

fun KSType.isJavaPrimitive(
    resolver: Resolver
) : Boolean {
    val builtIns = resolver.builtIns
    return equalsIgnoreNullable(builtIns.booleanType)
            || equalsIgnoreNullable(builtIns.byteType)
            || equalsIgnoreNullable(builtIns.shortType)
            || equalsIgnoreNullable(builtIns.intType)
            || equalsIgnoreNullable(builtIns.longType)
            || equalsIgnoreNullable(builtIns.floatType)
            || equalsIgnoreNullable(builtIns.charType)
            || equalsIgnoreNullable(builtIns.floatType)
            || equalsIgnoreNullable(builtIns.doubleType)
}

fun KSType.isJavaPrimitiveOrString(
    resolver: Resolver
): Boolean {
    return isJavaPrimitive(resolver) || this.makeNotNullable() == resolver.builtIns.stringType
}
