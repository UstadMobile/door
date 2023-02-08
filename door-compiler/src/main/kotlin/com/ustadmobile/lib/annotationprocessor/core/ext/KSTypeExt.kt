package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.findActualType
import com.ustadmobile.door.lifecycle.LiveData
import com.ustadmobile.door.paging.DataSourceFactory
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
 * or LiveData, DataSourceFactory, or Flow with a primitive or object type reference
 * e.g. Entity, List<Entity>, LiveData<Entity>, LiveData<List<Entity>> etc.
 *
 * @param resolver the KSP resolver
 *
 * @return The result type expected when running the actual query - either the object itself, or a list of objects. This
 * will unwrap LiveData, Flow, and DataSourceFactory.
 * e.g. Entity, List<Entity>, Entity, List<Entity> etc.
 *
 */
fun KSType.unwrapResultType(
    resolver: Resolver,
): KSType {
    val qualifiedName = this.declaration.qualifiedName?.asString()
    if (qualifiedName == LiveData::class.qualifiedName) {
        return this.arguments.first().type?.resolve()
            ?: throw IllegalArgumentException("unwrapLiveDataOrDataSourceFactoryResultType: Cannot resolve LiveData type!")
    } else if (qualifiedName == DataSourceFactory::class.qualifiedName) {
        val entityTypeRef = arguments.get(1).type ?: throw IllegalArgumentException("Factory has no type argument")
        return resolver.getClassDeclarationByName(resolver.getKSNameFromString("kotlin.collections.List"))
            ?.asType(listOf(resolver.getTypeArgument(entityTypeRef, Variance.INVARIANT)))
                ?: throw IllegalArgumentException("unwrapLiveDataOrDataSourceFactoryResultType: could not lookup datasource comp type")
    }else if(qualifiedName == Flow::class.qualifiedName) {
        return this.arguments.first().type?.resolve()
            ?: throw IllegalArgumentException("unwrapLiveDataOrDataSourceFactoryResultType: Cannot resolve Flow type!")
    }

    else {
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

private fun Resolver.sqliteIntegerTypes(): List<KSType> {
    return listOf(builtIns.booleanType, builtIns.byteType, builtIns.shortType, builtIns.intType, builtIns.longType)
        .flatMap { listOf(it, it.makeNullable()) }
}

private fun Resolver.sqliteRealTypes(): List<KSType> {
    return listOf(builtIns.floatType, builtIns.doubleType).flatMap { listOf(it, it.makeNullable()) }
}

fun KSType.equalsIgnoreNullable(otherType: KSType) : Boolean {
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

fun KSType.isDataSourceFactory() = declaration.isDataSourceFactory()

fun KSType.isPagingSource() = declaration.isPagingSource()

fun KSType.isLiveData() = declaration.isLiveData()

fun KSType.isPagingSourceOrDataSourceFactoryOrLiveDataOrFlow() = declaration.isPagingSourceOrDataSourceFactoryOrLiveDataOrFlow()

fun KSType.isFlow() = declaration.isFlow()

fun KSType.resolveActualTypeIfAliased(): KSType {
    return if(declaration is KSTypeAlias) {
        (declaration as KSTypeAlias).findActualType().asType(arguments)
    }else {
        this
    }
}
