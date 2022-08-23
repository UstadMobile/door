package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.door.lifecycle.LiveData
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.jdbc.TypesKmp

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

internal fun TypeName.isDataSourceFactory(paramTypeFilter: (List<TypeName>) -> Boolean = {true}): Boolean {
    return this is ParameterizedTypeName
            && this.rawType == com.ustadmobile.door.paging.DataSourceFactory::class.asClassName()
            && paramTypeFilter(this.typeArguments)
}


internal fun TypeName.isDataSourceFactoryOrLiveData() = this is ParameterizedTypeName
        && (this.isDataSourceFactory() ||  this.rawType == LiveData::class.asClassName())

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
 * If the given TypeName represents typed LiveData or a DataSource Factory, unwrap it to the
 * raw type.
 *
 * In the case of LiveData this is simply the first parameter type.
 * E.g. LiveData<Foo> will return 'Foo', LiveData<List<Foo>> will return List<Foo>
 *
 * In the case of a DataSourceFactory, this will be a list of the first parameter type (as a
 * DataSource.Factory is providing a list)
 * E.g. DataSource.Factory<Foo> will unwrap as List<Foo>
 */
fun TypeName.unwrapLiveDataOrDataSourceFactory()  =
    when {
        this is ParameterizedTypeName && rawType == LiveData::class.asClassName() -> typeArguments[0]
        this is ParameterizedTypeName && rawType == com.ustadmobile.door.paging.DataSourceFactory::class.asClassName() ->
            List::class.asClassName().parameterizedBy(typeArguments[1])
        else -> this
    }

/**
 * Unwrap the component type of an array or list
 */
fun TypeName.unwrapListOrArrayComponentType() =
        if(this is ParameterizedTypeName &&
                (this.rawType == List::class.asClassName() || this.rawType == ClassName("kotlin", "Array"))) {
            val typeArg = typeArguments[0]
            if(typeArg is WildcardTypeName) {
                typeArg.outTypes[0]
            }else {
                typeArg
            }
        }else {
            this
        }

/**
 * Unwrap everything that could be wrapping query return types. This will unwrap DataSource.Factory,
 * LiveData, List, and Array to give the singular type. This can be useful if you want to know
 * the type of entity that is being used.
 */
fun TypeName.unwrapQueryResultComponentType() = unwrapLiveDataOrDataSourceFactory().unwrapListOrArrayComponentType()


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

