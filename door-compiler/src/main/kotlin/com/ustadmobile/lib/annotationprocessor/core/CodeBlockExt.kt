package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.ColumnInfo
import com.squareup.kotlinpoet.*
import io.ktor.client.request.forms.*
import io.ktor.content.*
import io.ktor.http.*
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.lib.annotationprocessor.core.AbstractDbProcessor.Companion.MEMBERNAME_CLIENT_SET_BODY
import com.ustadmobile.lib.annotationprocessor.core.AbstractDbProcessor.Companion.MEMBERNAME_ENCODED_PATH

/**
 * Generate a delegation style function call, e.g.
 * varName.callMethod(param1, param2, param3)
 *
 * @param varName the variable name for the object that has the desired function
 * @param funSpec the function spec that we are generating a delegated call for
 */
fun CodeBlock.Builder.addDelegateFunctionCall(varName: String, funSpec: FunSpec) : CodeBlock.Builder {
    return add("$varName.${funSpec.name}(")
            .add(funSpec.parameters.filter { !isContinuationParam(it.type)}.joinToString { it.name })
            .add(")")
}

/**
 * Add a section to this CodeBlock that will declare a variable for the clientId and get it
 * from the header.
 *
 * e.g.
 * val clientIdVarName = call.request.header("X-nid")?.toLong() ?: 0
 *
 * @param varName the varname to create in the CodeBlock
 * @param serverType SERVER_TYPE_KTOR or SERVER_TYPE_NANOHTTPD
 *
 */
fun CodeBlock.Builder.addGetClientIdHeader(varName: String, serverType: Int) : CodeBlock.Builder {
    takeIf { serverType == DbProcessorKtorServer.SERVER_TYPE_KTOR }
            ?.add("val $varName = %M.request.%M(%S)?.toLong() ?: 0\n",
                    DbProcessorKtorServer.CALL_MEMBER,
                    MemberName("io.ktor.request","header"),
                    "x-nid")
    takeIf { serverType == DbProcessorKtorServer.SERVER_TYPE_NANOHTTPD }
            ?.add("val $varName = _session.headers.get(%S)?.toLong() ?: 0\n",
                "x-nid")

    return this
}

fun CodeBlock.Builder.beginIfNotNullOrEmptyControlFlow(varName: String, isList: Boolean) : CodeBlock.Builder{
    if(isList) {
        beginControlFlow("if(!$varName.isEmpty())")
    }else {
        beginControlFlow("if($varName != null)")
    }

    return this
}


/**
 * When generating code for a parameter we often want to add some statements that would run directly
 * on a given variable if it is a singular type, or use a forEach loop if it is a list or array.
 *
 * e.g.
 *
 * singular.changeSeqNum = 0
 *
 * or
 *
 * list.forEach {
 *     it.changeSeqNum = 0
 * }
 *
 * @param param the ParameterSpec that gives the type and the variable name
 * @param codeBlocks codeBlocks that should be run against each component of the parameter if it is
 * a list or array, or directly against the parameter if it is singular. Each will be automatically
 * prefixed with the parameter name for singular components, or "it" for lists and arrays
 * @return this
 */
fun CodeBlock.Builder.addRunCodeBlocksOnParamComponents(param: ParameterSpec, vararg codeBlocks: CodeBlock) : CodeBlock.Builder {
    if(param.type.isListOrArray()) {
        beginControlFlow("${param.name}.forEach")
        codeBlocks.forEach {
            add("it.")
            add(it)
        }
        endControlFlow()
    }else {
        codeBlocks.forEach {
            add("${param.name}.")
            add(it)
        }
    }

    return this
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
 * @param entityTypeSpec a TypeSpec that represents the entity a table is being created for
 * @param execSqlFn the literal string that should be added to call a function that runs SQL
 * @param dbProductType DoorDbType.SQLITE or POSTGRES
 * @param indices a list of IndexMirror representing the indices that should be added.
 */
fun CodeBlock.Builder.addCreateTableCode(
    entityTypeSpec: TypeSpec,
    packageName: String,
    execSqlFn: String,
    dbProductType: Int,
    processingEnv: ProcessingEnvironment,
    indices: List<IndexMirror> = listOf(),
    sqlListVar: String? = null
) : CodeBlock.Builder {
    addSql(execSqlFn, sqlListVar, entityTypeSpec.toCreateTableSql(dbProductType, packageName, processingEnv))
    indices.forEach {
        val indexName = if(it.name != "") {
            it.name
        }else {
            "index_${entityTypeSpec.name}_${it.value.joinToString(separator = "_", postfix = "", prefix = "")}"
        }

        addSql(execSqlFn, sqlListVar, "CREATE ${if(it.unique){ "UNIQUE " } else { "" } }INDEX $indexName" +
                " ON ${entityTypeSpec.name} (${it.value.joinToString()})")
    }

    entityTypeSpec.entityFields().forEach { field ->
        if(field.annotations.any { it.className == ColumnInfo::class.asClassName()
                        && it.members.findBooleanMemberValue("index") ?: false }) {
            addSql(execSqlFn, sqlListVar,
                    "CREATE INDEX index_${entityTypeSpec.name}_${field.name} ON ${entityTypeSpec.name} (${field.name})")
        }
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
    val httpResultType = funSpec.returnType?.unwrapLiveDataOrDataSourceFactory() ?: UNIT

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

/**
 * Generate the SQL that will be used to insert into the Zombie SQL trigger where an old attachment md5 is no longer in
 * use
 */
private fun TypeElement.generateZombieAttachmentInsertSql(): String {
    val attachmentInfo = EntityAttachmentInfo(this)
    val pkFieldName = enclosedElementsWithAnnotation(PrimaryKey::class.java).first().simpleName

    return """
        INSERT INTO ZombieAttachmentData(zaUri) 
        SELECT OLD.${attachmentInfo.uriPropertyName} AS zaUri
          FROM $entityTableName   
         WHERE ${entityTableName}.$pkFieldName = OLD.$pkFieldName
           AND (SELECT COUNT(*) 
                  FROM $entityTableName
                 WHERE ${attachmentInfo.md5PropertyName} = OLD.${attachmentInfo.md5PropertyName}) = 0
    """
}

/**
 * Add code that will generate triggers to catch Zombie attachment uris on SQLite
 */
fun CodeBlock.Builder.addGenerateAttachmentTriggerSqlite(
    entity: TypeElement,
    execSqlFn: String,
    stmtListVar: String? = null
) : CodeBlock.Builder{
    val attachmentInfo = EntityAttachmentInfo(entity)
    addSql(execSqlFn, stmtListVar, """
        CREATE TRIGGER ATTUPD_${entity.simpleName}
        AFTER UPDATE ON ${entity.simpleName} FOR EACH ROW WHEN
        OLD.${attachmentInfo.md5PropertyName} IS NOT NULL
        BEGIN
        ${entity.generateZombieAttachmentInsertSql()}; 
        END
    """)

    return this
}

/**
 * Add code that will generate triggers to catch Zombie attachment uris on Postgres
 */
fun CodeBlock.Builder.addGenerateAttachmentTriggerPostgres(entity: TypeElement, stmtListVar: String) : CodeBlock.Builder {
    val attachmentInfo = EntityAttachmentInfo(entity)
    add("$stmtListVar += %S\n", """
        CREATE OR REPLACE FUNCTION attach_${entity.simpleName}_fn() RETURNS trigger AS ${'$'}${'$'}
        BEGIN
        ${entity.generateZombieAttachmentInsertSql()};
        RETURN NEW;
        END ${'$'}${'$'}
        LANGUAGE plpgsql
    """.trimIndent())
    add("$stmtListVar += %S\n", """
        CREATE TRIGGER attach_${entity.simpleName}_trig
        AFTER UPDATE ON ${entity.simpleName}
        FOR EACH ROW WHEN (OLD.${attachmentInfo.md5PropertyName} IS NOT NULL)
        EXECUTE PROCEDURE attach_${entity.simpleName}_fn();
    """.trimIndent())

    return this
}
