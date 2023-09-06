package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Entity
import com.squareup.kotlinpoet.*
import io.ktor.client.request.forms.*
import io.ktor.content.*
import io.ktor.http.*
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.ustadmobile.lib.annotationprocessor.core.AbstractDbProcessor.Companion.MEMBERNAME_CLIENT_SET_BODY
import com.ustadmobile.lib.annotationprocessor.core.AbstractDbProcessor.Companion.MEMBERNAME_ENCODED_PATH
import com.ustadmobile.lib.annotationprocessor.core.ext.*

/**
 * Generate a delegation style function call, e.g.
 * varName.callMethod(param1, param2, param3)
 *
 * @param varName the variable name for the object that has the desired function
 * @param funSpec the function spec that we are generating a delegated call for
 */
fun CodeBlock.Builder.addDelegateFunctionCall(varName: String, funSpec: FunSpec) : CodeBlock.Builder {
    return add("$varName.${funSpec.name}(")
            .add(funSpec.parameters.joinToString { it.name })
            .add(")")
}


/**
 * Add SQL to the output. This could be done as appending to a list variable, or this can be done as a function call
 */
fun CodeBlock.Builder.addSql(
    execSqlFn: String?,
    sqlListVar: String? = null,
    sql: String
) : CodeBlock.Builder {
    if(sqlListVar != null) {
        add("$sqlListVar += %S\n", sql)
    }else {
        add("$execSqlFn(%S)\n", sql)
    }

    return this
}

/**
 * Add code that will create the table using a function that executes SQL (e.g. stmt.executeUpdate or
 * db.execSQL) to create a table and any indices specified. Indices can be specified by through
 * the indices argument (e.g. for those that come from the Entity annotation) and via the
 * entityTypeSpec for those that are specified using ColumnInfo(index=true) annotation.
 *
 * @param entityKSClass a KSClassDeclaration that represents the entity a table is being created for
 * @param execSqlFn the literal string that should be added to call a function that runs SQL
 * @param dbProductType DoorDbType.SQLITE or POSTGRES

 */
fun CodeBlock.Builder.addCreateTableCode(
    entityKSClass: KSClassDeclaration,
    execSqlFn: String,
    dbProductType: Int,
    sqlListVar: String? = null,
    resolver: Resolver,
) : CodeBlock.Builder {
    addSql(execSqlFn, sqlListVar, entityKSClass.toCreateTableSql(dbProductType, resolver))
    val entity = entityKSClass.getKSAnnotationByType(Entity::class)?.toEntity()

    entity?.indices?.forEach { index ->
        val indexName = if(index.name != "") {
            index.name
        }else {
            "index_${entityKSClass.entityTableName}_${index.value.joinToString(separator = "_", postfix = "", prefix = "")}"
        }

        addSql(execSqlFn, sqlListVar, "CREATE ${if(index.unique){ "UNIQUE " } else { "" } }INDEX $indexName" +
                " ON ${entityKSClass.entityTableName} (${index.value.joinToString()})")
    }

    return this
}


/**
 * Generates a CodeBlock that will make KTOR HTTP Client Request for a DAO method. It will set
 * the correct URL (e.g. endpoint/DatabaseName/DaoName/methodName and parameters (including the request body
 * if required). It will decide between using get or post based on the parameters.
 *
 * @param funSpec the FunSpec that represents the function for which we expect an endpoint on
 * the server
 * @param httpClientVarName variable name to access a KTOR httpClient
 * @param httpEndpointVarName variable name for the base API endpoint
 * @param daoName The name of the DAO to which funSpec belongs
 */
internal fun CodeBlock.Builder.addKtorRequestForFunction(
    funSpec: FunSpec,
    httpClientVarName: String = "_httpClient",
    httpEndpointVarName: String = "_endpoint",
    daoName: String,
    useKotlinxListSerialization: Boolean = false,
    kotlinxSerializationJsonVarName: String = "",
    useMultipartPartsVarName: String? = null,
    addNodeIdAndVersionRepoVarName: String? = "_repo",
    addClientIdHeaderVar: String? = null): CodeBlock.Builder {

    //Begin creation of the HttpStatement call that will set the URL, parameters, etc.
    val nonQueryParams =  funSpec.parameters.filter { !it.type.isHttpQueryQueryParam() }

    //The type of the response we expect from the server.
    val httpResultType = funSpec.returnType

    val httpMemberFn = if(nonQueryParams.isEmpty()) {
        CLIENT_GET_MEMBER_NAME
    }else {
        CLIENT_POST_MEMBER_NAME
    }

    beginControlFlow("$httpClientVarName.%M",
            httpMemberFn)
    beginControlFlow("url")
    add("%M($httpEndpointVarName)\n", MemberName("io.ktor.http", "takeFrom"))
    add("%M = \"\${encodedPath}%L/%L\"\n", MEMBERNAME_ENCODED_PATH, daoName, funSpec.name)
    endControlFlow()

    if(addNodeIdAndVersionRepoVarName != null) {
        add("%M($addNodeIdAndVersionRepoVarName)\n",
                MemberName("com.ustadmobile.door.ext", "doorNodeAndVersionHeaders"))
    }

    if(addClientIdHeaderVar != null) {
        add("%M(%S, $addClientIdHeaderVar)\n", MemberName("io.ktor.client.request", "header"),
                "x-nid")
    }

    funSpec.parameters.filter { it.type.isHttpQueryQueryParam() }.forEach {
        val paramType = it.type
        val isList = paramType is ParameterizedTypeName && paramType.rawType == List::class.asClassName()

        val paramsCodeblock = CodeBlock.builder()
        var paramVarName = it.name
        if(isList) {
            paramsCodeblock.add("${it.name}.forEach { ")
            paramVarName = "it"
            if(paramType != String::class.asClassName()) {
                paramVarName += ".toString()"
            }
        }

        paramsCodeblock.add("%M(%S, $paramVarName)\n",
                MemberName("io.ktor.client.request", "parameter"),
                it.name)
        if(isList) {
            paramsCodeblock.add("} ")
        }
        paramsCodeblock.add("\n")
        addWithNullCheckIfNeeded(it.name, it.type, paramsCodeblock.build())
    }

    val requestBodyParam = funSpec.parameters.firstOrNull { !it.type.isHttpQueryQueryParam() }

    if(requestBodyParam != null) {
        val requestBodyParamType = requestBodyParam.type

        val writeBodyCodeBlock = if(useMultipartPartsVarName != null) {
            CodeBlock.of("body = %T($useMultipartPartsVarName)\n",
                    MultiPartFormDataContent::class)
        }else if(useKotlinxListSerialization && requestBodyParamType is ParameterizedTypeName
                && requestBodyParamType.rawType == List::class.asClassName()) {
            val entityComponentType = resolveEntityFromResultType(requestBodyParamType).javaToKotlinType()
            val serializerFnCodeBlock = if(entityComponentType in QUERY_SINGULAR_TYPES) {
                CodeBlock.of("%M()", MemberName("kotlinx.serialization", "serializer"))
            }else {
                CodeBlock.of("serializer()")
            }
            CodeBlock.of("%M(%T(_json.stringify(%T.%L.%M, ${requestBodyParam.name}), %T.Application.Json.%M()))\n",
                    MEMBERNAME_CLIENT_SET_BODY,
                    TextContent::class, entityComponentType,
                    serializerFnCodeBlock,
                    MemberName("kotlinx.serialization.builtins", "list"),
                    ContentType::class,
                    MemberName("com.ustadmobile.door.ext", "withUtf8Charset")
            )
        }else {
            CodeBlock.of("%M(%M().write(${requestBodyParam.name}, %T.Application.Json.%M()))\n",
                    MEMBERNAME_CLIENT_SET_BODY,
                    MemberName("io.ktor.client.plugins.json", "defaultSerializer"),
                    ContentType::class, MemberName("com.ustadmobile.door.ext", "withUtf8Charset")
            )
        }

        addWithNullCheckIfNeeded(requestBodyParam.name, requestBodyParam.type,
                writeBodyCodeBlock)
    }

    unindent().add("}.")
    if(httpResultType.isNullable)
        add("%M()", BODY_OR_NULL_MEMBER_NAME)
    else
        add("%M()", BODY_MEMBER_NAME)
    add("\n")

    return this
}


/**
 * Shorthand to begin a runBlocking control flow
 */
fun CodeBlock.Builder.beginRunBlockingControlFlow() =
        add("%M·{\n", MemberName("kotlinx.coroutines", "runBlocking"))
                .indent()


enum class PreparedStatementOp() {
    GET, SET;

    override fun toString() = if(this == GET) {
        "get"
    }else {
        "set"
    }
}

/**
 * Generate code that will get a value back from the ResultSet of the desired type
 * e.g. getInt, getString, etc. or generate code that will set a parameter on a preparedstatement
 * e.g. setInt, setString etc.
 *
 * @param type the property type that is being used
 * @param operation GET or SET
 * @param resolver KSP resolver
 *
 * @return CodeBlock with the correct get/set call. This does not include brackets. It will use extension
 * functions to handle nullable fields as required.
 */
fun CodeBlock.Builder.addGetResultOrSetQueryParamCall(
    type: KSType,
    operation: PreparedStatementOp,
    resolver: Resolver
): CodeBlock.Builder {
    val builtIns = resolver.builtIns
    val extPkgName = "com.ustadmobile.door.jdbc.ext"
    when {
        type == builtIns.intType -> add("${operation}Int")
        type == builtIns.intType.makeNullable() -> add("%M",
            MemberName(extPkgName, "${operation}IntNullable"))
        type == builtIns.shortType -> add("${operation}Short")
        type == builtIns.shortType.makeNullable() -> MemberName
        type == builtIns.byteType -> add("${operation}Byte")
        type == builtIns.byteType.makeNullable() -> add("%M",
            MemberName(extPkgName, "${operation}ByteNullable"))
        type == builtIns.longType -> add("${operation}Long")
        type == builtIns.longType.makeNullable() -> add("%M",
            MemberName(extPkgName, "${operation}LongNullable"))
        type == builtIns.floatType -> add("${operation}Float")
        type == builtIns.floatType.makeNullable() -> add("%M",
            MemberName(extPkgName,"${operation}Float"))
        type == builtIns.doubleType -> add("${operation}Double")
        type == builtIns.doubleType.makeNullable() -> add("%M",
            MemberName(extPkgName, "${operation}Double"))
        type == builtIns.booleanType -> add("${operation}Boolean")
        type == builtIns.booleanType.makeNullable() -> add("%M",
            MemberName(extPkgName, "${operation}Boolean"))
        type.equalsIgnoreNullable(builtIns.stringType) -> add("${operation}String")
        type == builtIns.arrayType -> add("${operation}Array")
        (type.declaration as? KSClassDeclaration)?.isListDeclaration() == true -> add("${operation}Array")
        else -> add("ERR_UNKNOWN_TYPE /* $type */")
    }

    return this
}

/**
 * Create a PreparedStatement set param call for the given variable type
 */
fun CodeBlock.Builder.addPreparedStatementSetCall(
    type: KSType,
    resolver: Resolver
) = addGetResultOrSetQueryParamCall(type, PreparedStatementOp.SET, resolver)
