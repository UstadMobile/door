package com.ustadmobile.lib.annotationprocessor.core

import com.squareup.kotlinpoet.*
import com.ustadmobile.door.jdbc.TypesKmp
import kotlinx.coroutines.flow.Flow
/**
 * Map a Kotlin Type to JDBC Types constant
 */
fun TypeName.toSqlTypesInt() = when {
    this == BOOLEAN -> TypesKmp.BOOLEAN
    this == BYTE -> TypesKmp.SMALLINT
    this == SHORT -> TypesKmp.SMALLINT
    this == INT -> TypesKmp.INTEGER
    this == LONG -> TypesKmp.BIGINT
    this == FLOAT -> TypesKmp.FLOAT
    this == DOUBLE -> TypesKmp.DOUBLE
    this == String::class.asClassName() -> TypesKmp.LONGVARCHAR
    this == String::class.asClassName().copy(nullable = true) -> TypesKmp.LONGVARCHAR
    else -> throw IllegalArgumentException("Could not get sqlTypeInt for: $this")
}

internal fun TypeName.isPagingSource(): Boolean {
    return this is ParameterizedTypeName
            && this.rawType == com.ustadmobile.door.paging.PagingSource::class.asClassName()
}

internal fun TypeName.isFlow(): Boolean {
    return this is ParameterizedTypeName && this.rawType == Flow::class.asClassName()
}


internal fun TypeName.isAsynchronousReturnType() = this is ParameterizedTypeName
        && (isPagingSource() || isFlow ())

fun TypeName.isListOrArray() = (this is ClassName && this.canonicalName =="kotlin.Array")
        || (this is ParameterizedTypeName && this.rawType == List::class.asClassName())

/**
 * Determines whether or not this TypeName is nullable when it is used as a return type for a select
 * query. This will return false for any primitive, false for List types (which must be an empty list
 * when there is no result), false for DataSource.Factory, and true for Strings and singular entity
 * types.
 */
val TypeName.isNullableAsSelectReturnResult
    get() = this != UNIT
            && !PRIMITIVE.contains(this)
            && this !is ParameterizedTypeName


/**
 * Check if this TypeName should be sent as query parameters when passed over http. This is true
 * for primitive types, strings, and lists thereof. It is false for other types (e.g. entities themselves)
 */
fun TypeName.isHttpQueryQueryParam(): Boolean {
    return this in QUERY_SINGULAR_TYPES
            || (this is ParameterizedTypeName
            && (this.rawType == List::class.asClassName() && this.typeArguments[0] in QUERY_SINGULAR_TYPES))
}

/**
 * Gets the default value for this typename. This is 0 for primitive numbers, false for booleans,
 * null for strings, empty listOf for list types
 */
fun TypeName.defaultTypeValueCode(): CodeBlock {
    val codeBlock = CodeBlock.builder()
    when(val kotlinType = javaToKotlinType()) {
        INT -> codeBlock.add("0")
        LONG -> codeBlock.add("0L")
        BYTE -> codeBlock.add("0.toByte()")
        FLOAT -> codeBlock.add("0.toFloat()")
        DOUBLE -> codeBlock.add("0.toDouble()")
        BOOLEAN -> codeBlock.add("false")
        String::class.asTypeName() -> codeBlock.add("null as String?")
        else -> {
            if(kotlinType is ParameterizedTypeName && kotlinType.rawType == List::class.asClassName()) {
                codeBlock.add("mutableListOf<%T>()", kotlinType.typeArguments[0])
            }else {
                codeBlock.add("null as %T?", this)
            }
        }
    }

    return codeBlock.build()
}

fun TypeName.isArrayType(): Boolean = (this is ParameterizedTypeName && this.rawType.canonicalName == "kotlin.Array")

