package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.*
import com.squareup.kotlinpoet.*
import com.ustadmobile.door.jdbc.*
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.tools.Diagnostic
import java.io.File
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.*
import com.ustadmobile.door.annotation.*
import io.ktor.http.HttpStatusCode
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.*
import com.ustadmobile.door.entities.ChangeLog
import com.ustadmobile.door.ext.minifySql
import com.ustadmobile.lib.annotationprocessor.core.ext.toSql
import io.github.aakira.napier.Napier
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.statement.HttpStatement
import io.ktor.http.Headers

//here in a comment because it sometimes gets removed by 'optimization of parameters'
// import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy


val SQL_NUMERIC_TYPES = listOf(BYTE, SHORT, INT, LONG, FLOAT, DOUBLE)

val PARAM_NAME_OFFSET = "offset"

val PARAM_NAME_LIMIT = "limit"

fun defaultSqlQueryVal(typeName: TypeName) = if(typeName in SQL_NUMERIC_TYPES) {
    "0"
}else if(typeName == BOOLEAN){
    "false"
}else {
    "null"
}

fun findEntitiesWithAnnotation(entityType: ClassName, annotationClass: Class<out Annotation>,
                               processingEnv: ProcessingEnvironment,
                               embedPath: List<String> = listOf()): Map<List<String>, ClassName> {
    if(entityType in QUERY_SINGULAR_TYPES)
        return mapOf()


    val entityTypeEl = processingEnv.elementUtils.getTypeElement(entityType.canonicalName)
    if(entityTypeEl == null){
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "Entity type: " + entityType.canonicalName)

    }
    val syncableEntityList = mutableMapOf<List<String>, ClassName>()
    ancestorsToList(entityTypeEl, processingEnv).forEach {
        if(it.getAnnotation(annotationClass) != null)
            syncableEntityList.put(embedPath, it.asClassName())

        it.enclosedElements.filter { it.getAnnotation(Embedded::class.java) != null}.forEach {
            val subEmbedPath = mutableListOf(*embedPath.toTypedArray()) + "${it.simpleName}"
            syncableEntityList.putAll(findEntitiesWithAnnotation(it.asType().asTypeName() as ClassName,
                    annotationClass, processingEnv, subEmbedPath))
        }
    }

    return syncableEntityList.toMap()
}

fun jdbcDaoTypeSpecBuilder(simpleName: String, superTypeName: TypeName) = TypeSpec.classBuilder(simpleName)
        .primaryConstructor(FunSpec.constructorBuilder().addParameter("_db",
                DoorDatabase::class).build())
        .addProperty(PropertySpec.builder("_db", DoorDatabase::class).initializer("_db").build())
        .superclass(superTypeName)


/**
 * Determine if the given
 */
@Deprecated("Use TypeNameExt.isHttpQueryQueryParam instead")
internal fun isQueryParam(typeName: TypeName) = typeName in QUERY_SINGULAR_TYPES
        || (typeName is ParameterizedTypeName
        && (typeName.rawType == List::class.asClassName() && typeName.typeArguments[0] in QUERY_SINGULAR_TYPES))


/**
 * Given a list of parameters, get a list of those that get not pass as query parameters over http.
 * This is any parameters except primitive types, strings, or lists and arrays thereof
 *
 * @param params List of parameters to check for which ones cannot be passed as query parameters
 * @return List of parameters from the input list which cannot be passed as http query parameters
 */
internal fun getHttpBodyParams(params: List<ParameterSpec>) = params.filter {
    !isQueryParam(it.type) && !isContinuationParam(it.type)
}


/**
 * Given a list of http parameters, find the first, if any, which should be sent as the http body
 */
internal fun getRequestBodyParam(params: List<ParameterSpec>) = params.firstOrNull {
    !isQueryParam(it.type) && !isContinuationParam(it.type)
}


internal val CLIENT_GET_MEMBER_NAME = MemberName("io.ktor.client.request", "get")

internal val CLIENT_POST_MEMBER_NAME = MemberName("io.ktor.client.request", "post")

internal val CLIENT_GET_NULLABLE_MEMBER_NAME = MemberName("com.ustadmobile.door.ext", "getOrNull")

internal val CLIENT_POST_NULLABLE_MEMBER_NAME = MemberName("com.ustadmobile.door.ext", "postOrNull")

internal val CLIENT_RECEIVE_MEMBER_NAME = MemberName("io.ktor.client.call", "receive")

internal val CLIENT_PARAMETER_MEMBER_NAME = MemberName("io.ktor.client.request", "parameter")

internal val CLIENT_HTTPSTMT_RECEIVE_MEMBER_NAME = MemberName("io.ktor.client.call", "receive")

/**
 * Generates a CodeBlock that will make KTOR HTTP Client Request for a DAO method. It will set
 * the correct URL (e.g. endpoint/DatabaseName/DaoName/methodName and parameters (including the request body
 * if required). It will decide between using get or post based on the parameters.
 *
 * @param httpEndpointVarName the variable name that contains the base http endpoint to start with for the url
 * @param dbPathVarName the variable name that contains the name of the database
 * @param daoName the DAO name (e.g. simple class name of the DAO class)
 * @param methodName the name of the method that is being queried
 * @param httpStatementVarName the variable name that will be added to the codeblock that will contain
 * the http statement object
 * @param httpResponseHeadersVarName the variable name that will be used to capture the http headers
 * received from the response (after the .execute call is done)
 * @param httpResultType the type of response expected from the other end (e.g. the result type of
 * the method)
 * @param params a list of the parameters (e.g. from the method signature) that need to be sent
 * @param useKotlinxListSerialization if true, the generated code will use the Kotlinx Json object
 * to serialize and deserialize lists. This is because the Javascript client (using Kotlinx serialization)
 * will not automatically handle .receive<List<Entity>>
 * @param kotlinxSerializationJsonVarName if useKotlinxListSerialization, thne this is the variable
 * name that will be used to access the Json object to serialize or deserialize.
 * entities. If false, we will use the local change sequence number.
 *
 * REPLACE WITH CodeBlockExt.addKtorRequestForFunction
 */
internal fun generateKtorRequestCodeBlockForMethod(httpEndpointVarName: String = "_endpoint",
                                                   dbPathVarName: String,
                                                   daoName: String,
                                                   methodName: String,
                                                   httpStatementVarName: String = "_httpStatement",
                                                   httpResponseHeadersVarName: String = "_httpResponseHeaders",
                                                   httpResultVarName: String = "_httpResult",
                                                   requestBuilderCodeBlock: CodeBlock = CodeBlock.of(""),
                                                   httpResultType: TypeName,
                                                   params: List<ParameterSpec>,
                                                   useKotlinxListSerialization: Boolean = false,
                                                   kotlinxSerializationJsonVarName: String = "",
                                                   useMultipartPartsVarName: String? = null,
                                                   addVersionAndNodeIdArg: String? = "_repo"): CodeBlock {

    //Begin creation of the HttpStatement call that will set the URL, parameters, etc.
    val nonQueryParams = getHttpBodyParams(params)
    val codeBlock = CodeBlock.builder()
            .beginControlFlow("val $httpStatementVarName = _httpClient.%M<%T>",
                    if(nonQueryParams.isNullOrEmpty()) CLIENT_GET_MEMBER_NAME else CLIENT_POST_MEMBER_NAME,
                    HttpStatement::class)
            .beginControlFlow("url")
            .add("%M($httpEndpointVarName)\n", MemberName("io.ktor.http", "takeFrom"))
            .add("encodedPath = \"\${encodedPath}\${$dbPathVarName}/%L/%L\"\n", daoName, methodName)
            .endControlFlow()
            .add(requestBuilderCodeBlock)



    codeBlock.takeIf { addVersionAndNodeIdArg != null }?.add("%M($addVersionAndNodeIdArg)\n",
            MemberName("com.ustadmobile.door.ext", "doorNodeAndVersionHeaders"))

    params.filter { isQueryParam(it.type) }.forEach {
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
        codeBlock.addWithNullCheckIfNeeded(it.name, it.type,
                paramsCodeblock.build())
    }

    val requestBodyParam = getRequestBodyParam(params)

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
            CodeBlock.of("body = %T(_json.stringify(%T.%L.%M, ${requestBodyParam.name}), %T.Application.Json.%M())\n",
                TextContent::class, entityComponentType,
                    serializerFnCodeBlock,
                    MemberName("kotlinx.serialization.builtins", "list"),
                    ContentType::class,
                    MemberName("com.ustadmobile.door.ext", "withUtf8Charset"))
        }else {
            CodeBlock.of("body = %M().write(${requestBodyParam.name}, %T.Application.Json.%M())\n",
                    MemberName("io.ktor.client.features.json", "defaultSerializer"),
                    ContentType::class, MemberName("com.ustadmobile.door.ext", "withUtf8Charset"))
        }

        codeBlock.addWithNullCheckIfNeeded(requestBodyParam.name, requestBodyParam.type,
                writeBodyCodeBlock)
    }

    codeBlock.endControlFlow()

    //End creation of the HttpStatement

    codeBlock.add("var $httpResponseHeadersVarName: %T? = null\n", Headers::class)

    val receiveCodeBlock = if(useKotlinxListSerialization && httpResultType is ParameterizedTypeName
            && httpResultType.rawType == List::class.asClassName() ) {
        val serializerFnCodeBlock = if(httpResultType.typeArguments[0].javaToKotlinType() in QUERY_SINGULAR_TYPES) {
            CodeBlock.of("%M()", MemberName("kotlinx.serialization", "serializer"))
        }else {
            CodeBlock.of("serializer()")
        }
        CodeBlock.of("$kotlinxSerializationJsonVarName.parse(%T.%L.%M, $httpStatementVarName.%M<String>())\n",
                httpResultType.typeArguments[0], serializerFnCodeBlock,
                MemberName("kotlinx.serialization.builtins", "list"),
                CLIENT_RECEIVE_MEMBER_NAME)
    }else{
        CodeBlock.Builder().beginControlFlow("$httpStatementVarName.execute")
                .add(" response ->\n")
                .add("$httpResponseHeadersVarName = response.headers\n")
                .apply { takeIf { httpResultType.isNullable }
                        ?.beginControlFlow(
                            "if(response.status == %T.NoContent)", HttpStatusCode::class)
                            ?.add("null\n")
                            ?.nextControlFlow("else")
                }
                .add("response.%M<%T>()\n", CLIENT_HTTPSTMT_RECEIVE_MEMBER_NAME, httpResultType)
                .apply { takeIf { httpResultType.isNullable }?.endControlFlow() }
                .endControlFlow()
                .build()

    }

    codeBlock.add("val $httpResultVarName = ")
    codeBlock.add(receiveCodeBlock)


    return codeBlock.build()
}


/**
 * Will add the given codeblock, and surround it with if(varName != null) if the given typename
 * is nullable
 */
fun CodeBlock.Builder.addWithNullCheckIfNeeded(varName: String, typeName: TypeName,
                                               codeBlock: CodeBlock): CodeBlock.Builder {
    if(typeName.isNullable)
        beginControlFlow("if($varName != null)")

    add(codeBlock)

    if(typeName.isNullable)
        endControlFlow()

    return this
}

fun getEntityPrimaryKey(entityEl: TypeElement) = entityEl.enclosedElements
        .firstOrNull { it.kind == ElementKind.FIELD && it.getAnnotation(PrimaryKey::class.java) != null}


/**
 *
 */
fun findEntityModifiedByQuery(querySql: String, allKnownEntityNames: List<String>): String? {
    //Split the statement up. There might be a statement like
    // INSERT INTO TableName(fieldName)
    // Therefor the regex will split by space or open bracket
    val stmtSplit = querySql.trim().split(Regex("(\\s|\\()+"), limit = 4)
    val sqlKeyword = stmtSplit[0].uppercase()
    val tableModified = if(sqlKeyword == "UPDATE") {
        stmtSplit[1] // in case it is an update statement, will be the second word (e.g. update tablename)
    }else if(sqlKeyword == "DELETE"){
        stmtSplit[2] // in case it is a delete statement, will be the third word (e.g. delete from tablename)
    }else if(sqlKeyword == "INSERT" || sqlKeyword == "REPLACE") {
        stmtSplit[2] //in case it is an INSERT INTO or REPLACE INTO statement, will be the third word (e.g. INSERT INTO tablename)
    } else {
        null
    }

    /*
     * If the entity did not exist, then our attempt to run the query would have thrown
     * an SQLException . When calling handleTableChanged, we want to use the same case
     * as the entity, so we look it up from the list of known entities to find the correct
     * case to use.
     */
    return if(tableModified != null) {
        allKnownEntityNames.first { it.equals(tableModified,  ignoreCase = true) }
    }else {
        null
    }
}

/**
 * The parent class of all processors that generate implementations for door. Child processors
 * should implement process.
 */
abstract class AbstractDbProcessor: AbstractProcessor() {

    protected lateinit var messager: Messager

    protected var dbConnection: Connection? = null

    /**
     * When we generate the code for a Query annotation function that performs an update or delete,
     * we use this so that we can match the case of the table name. This will be setup by
     * AnnotationProcessorWrapper calling processDb.
     */
    protected var allKnownEntityNames = mutableListOf<String>()

    /**
     * Provides a map that can be used to find the TypeElement for a given table name. This will be
     * setup by AnnotationProcessorWrapper calling processDb.
     */
    protected var allKnownEntityTypesMap = mutableMapOf<String, TypeElement>()

    /**
     * Initiates internal info about the databases that are being processed, then calls the main
     * process function. This is called by AnnotationProcessorWrapper
     *
     * @param annotations as per the main annotation processor process method
     * @param roundEnv as per the main annotation processor process method
     * @param dbConnection a JDBC Connection object that can be used to run queries
     * @param allKnownEntityNames as per the allKnownEntityNames property
     * @param allKnownEntityTypesMap as per the allKnownEntityTypesMap property
     */
    fun processDb(annotations: MutableSet<out TypeElement>,
                           roundEnv: RoundEnvironment,
                           dbConnection: Connection,
                           allKnownEntityNames: MutableList<String>,
                           allKnownEntityTypesMap: MutableMap<String, TypeElement>)  : Boolean {
        this.allKnownEntityNames = allKnownEntityNames
        this.allKnownEntityTypesMap = allKnownEntityTypesMap
        this.dbConnection = dbConnection
        return process(annotations, roundEnv)
    }

    override fun init(p0: ProcessingEnvironment) {
        super.init(p0)
        messager = p0.messager
    }

    /**
     * Add triggers that will insert into the ChangeLog table
     */
    protected fun CodeBlock.Builder.addReplicateEntityChangeLogTrigger(
        entityType: TypeElement,
        sqlListVar: String,
        dbProductType: Int,
    ) : CodeBlock.Builder{
        val replicateEntity = entityType.getAnnotation(ReplicateEntity::class.java)
        val primaryKeyEl = entityType.entityPrimaryKey
            ?: throw IllegalArgumentException("addReplicateEntityChangeLogTrigger ${entityType.qualifiedName} has NO PRIMARY KEY!")

        data class TriggerParams(val opName: String, val prefix: String, val opCode: Int) {
            val opPrefix = opName.lowercase().substring(0, 3)
        }

        if(dbProductType == DoorDbType.SQLITE) {
            val triggerParams = listOf(
                TriggerParams("INSERT", "NEW", ChangeLog.CHANGE_UPSERT),
                TriggerParams("UPDATE", "NEW", ChangeLog.CHANGE_UPSERT),
                TriggerParams("DELETE", "OLD", ChangeLog.CHANGE_DELETE)
            )

            triggerParams.forEach { params ->
                /*
                Note: REPLACE INTO etc. does not work because the conflict policy will be determined by the statement
                triggering this as per https://sqlite.org/lang_createtrigger.html Section 2.
                "An ON CONFLICT clause may be specified as part of an UPDATE or INSERT action within the body of the
                trigger. However if an ON CONFLICT clause is specified as part of the statement causing the trigger to
                 fire, then conflict handling policy of the outer statement is used instead."
                 */
                add("$sqlListVar += %S\n",
                    """
                CREATE TRIGGER ch_${params.opPrefix}_${replicateEntity.tableId}
                       AFTER ${params.opName} ON ${entityType.entityTableName}
                BEGIN
                       INSERT INTO ChangeLog(chTableId, chEntityPk, chType)
                       SELECT ${replicateEntity.tableId} AS chTableId, 
                              ${params.prefix}.${primaryKeyEl.simpleName} AS chEntityPk, 
                              ${params.opCode} AS chType
                        WHERE NOT EXISTS(
                              SELECT chTableId 
                                FROM ChangeLog 
                               WHERE chTableId = ${replicateEntity.tableId}
                                 AND chEntityPk = ${params.prefix}.${primaryKeyEl.simpleName}); 
                END
                """.minifySql())
            }
        }else {
            val triggerParams = listOf(
                TriggerParams("UPDATE OR INSERT", "NEW", ChangeLog.CHANGE_UPSERT),
                TriggerParams("DELETE", "OLD", ChangeLog.CHANGE_DELETE))
            triggerParams.forEach { params ->
                add("$sqlListVar += %S\n",
                    """
               CREATE OR REPLACE FUNCTION 
               ch_${params.opPrefix}_${replicateEntity.tableId}_fn() RETURNS TRIGGER AS $$
               BEGIN
               INSERT INTO ChangeLog(chTableId, chEntityPk, chType)
                       VALUES (${replicateEntity.tableId}, ${params.prefix}.${primaryKeyEl.simpleName}, ${params.opCode})
               ON CONFLICT(chTableId, chEntityPk) DO UPDATE
                       SET chType = ${params.opCode};
               RETURN NULL;
               END $$
               LANGUAGE plpgsql         
            """.minifySql())
                add("$sqlListVar += %S\n",
                    """
            CREATE TRIGGER ch_${params.opPrefix}_${replicateEntity.tableId}_trig 
                   AFTER ${params.opName} ON ${replicateEntity.tableId}
                   FOR EACH ROW
                   EXECUTE PROCEDURE ch_${params.opPrefix}_${replicateEntity.tableId}_fn();
            """.minifySql())
            }


        }

        return this
    }

    /**
     * Add a ReceiveView for the given EntityTypeElement.
     */
    protected fun CodeBlock.Builder.addCreateReceiveView(
        entityTypeEl: TypeElement,
        sqlListVar: String
    ): CodeBlock.Builder {
        val trkrEl = entityTypeEl.getReplicationTracker(processingEnv)
        val receiveViewAnn = entityTypeEl.getAnnotation(ReplicateReceiveView::class.java)
        val viewName = receiveViewAnn?.name ?: "${entityTypeEl.entityTableName}$SUFFIX_DEFAULT_RECEIVEVIEW"
        val sql = receiveViewAnn?.value ?: """
            SELECT ${entityTypeEl.simpleName}.*, ${trkrEl.entityTableName}.*
              FROM ${entityTypeEl.simpleName}
                   LEFT JOIN ${trkrEl.simpleName} ON ${trkrEl.entityTableName}.${trkrEl.replicationTrackerForeignKey.simpleName} = 
                        ${entityTypeEl.entityTableName}.${entityTypeEl.entityPrimaryKey?.simpleName}
        """.minifySql()
        add("$sqlListVar += %S\n", "CREATE VIEW $viewName AS $sql")
        return this
    }



    protected fun CodeBlock.Builder.addCreateTriggersCode(
        entityType: TypeElement,
        stmtListVar: String,
        dbProductType: Int
    ): CodeBlock.Builder {
        Napier.d("Door Wrapper: addCreateTriggersCode ${entityType.simpleName}")
        entityType.getAnnotationsByType(Triggers::class.java).firstOrNull()?.value?.forEach { trigger ->
            trigger.toSql(entityType, dbProductType).forEach { sqlStr ->
                add("$stmtListVar += %S\n", sqlStr)
            }
        }

        return this
    }


    /**
     * Generate a codeblock with the JDBC code required to perform a query and return the given
     * result type
     *
     * @param returnType the return type of the query
     * @param queryVars: map of String (variable name) to the type of parameter. Used to set
     * parameters on the preparedstatement
     * @param querySql The actual query SQL itself (e.g. as per the Query annotation)
     * @param enclosing TypeElement (e.g the DAO) in which it is enclosed, used to resolve parameter types
     * @param method The method that this implementation is being generated for. Used for error reporting purposes
     * @param resultVarName The variable name for the result of the query (this will be as per resultType,
     * with any wrapping (e.g. LiveData) removed.
     */
    //TODO: Check for invalid combos. Cannot have querySql and rawQueryVarName as null. Cannot have rawquery doing update
    fun CodeBlock.Builder.addJdbcQueryCode(
        returnType: TypeName,
        queryVars: Map<String, TypeName>,
        querySql: String?,
        enclosing: TypeElement?,
        method: ExecutableElement?,
        resultVarName: String = "_result",
        rawQueryVarName: String? = null,
        suspended: Boolean = false,
        querySqlPostgres: String? = null
    ): CodeBlock.Builder {
        // The result, with any wrapper (e.g. LiveData or DataSource.Factory) removed
        val resultType = resolveQueryResultType(returnType)

        // The individual entity type e.g. Entity or String etc
        val entityType = resolveEntityFromResultType(resultType)

        val entityTypeElement = if(entityType is ClassName) {
            processingEnv.elementUtils.getTypeElement(entityType.canonicalName)
        } else {
            null
        }

        val resultEntityField = if(entityTypeElement != null) {
            ResultEntityField(null, "_entity", entityTypeElement.asClassName(),
                    entityTypeElement, processingEnv)
        }else {
            null
        }

        val isUpdateOrDelete = querySql != null && querySql.isSQLAModifyingQuery()


        val preparedStatementSql = querySql?.replaceQueryNamedParamsWithQuestionMarks()

        if(preparedStatementSql != null) {
            val namedParams = preparedStatementSql.getSqlQueryNamedParameters()

            val missingParams = namedParams.filter { it !in queryVars.keys }
            if(missingParams.isNotEmpty()) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "On ${enclosing?.qualifiedName}.${method?.simpleName} has the following named " +
                        "params in query that are not parameters of the function: ${missingParams.joinToString()}")
            }
        }

        val preparedStatementSqlPostgres = querySqlPostgres ?: querySql?.replaceQueryNamedParamsWithQuestionMarks()
            ?.sqlToPostgresSql()


        if(resultType != UNIT)
            add("var $resultVarName = ${defaultVal(resultType)}\n")

        if(rawQueryVarName == null) {
            add("val _stmtConfig = %T(%S ", PreparedStatementConfig::class, preparedStatementSql)
            if(queryVars.any { it.value.javaToKotlinType().isListOrArray() })
                add(",hasListParams = true")

            if(preparedStatementSql != preparedStatementSqlPostgres)
                add(", postgreSql = %S", preparedStatementSqlPostgres)

            add(")\n")

        }else {
            add("val _stmtConfig = %T($rawQueryVarName.getSql(), hasListParams = $rawQueryVarName.%M())\n",
                PreparedStatementConfig::class, MemberName("com.ustadmobile.door.ext", "hasListOrArrayParams"))
        }


        beginControlFlow("_db.%M(_stmtConfig)", prepareAndUseStatmentMemberName(suspended))
        add("_stmt ->\n")

        if(querySql != null) {
            var paramIndex = 1
            val queryVarsNotSubstituted = mutableListOf<String>()
            querySql.getSqlQueryNamedParameters().forEach {
                val paramType = queryVars[it]
                if(paramType == null ) {
                    queryVarsNotSubstituted.add(it)
                }else if(paramType.javaToKotlinType().isListOrArray()) {
                    //val con = null as Connection
                    val arrayTypeName = sqlArrayComponentTypeOf(paramType.javaToKotlinType())
                    add("_stmt.setArray(${paramIndex++}, _db.createArrayOf(_stmt.getConnection(), %S, %L.toTypedArray()))\n",
                        arrayTypeName, it)
                }else {
                    add("_stmt.set${paramType.javaToKotlinType().preparedStatementSetterGetterTypeName}(${paramIndex++}, " +
                            "${it})\n")
                }
            }

            if(queryVarsNotSubstituted.isNotEmpty()) {
                logMessage(Diagnostic.Kind.ERROR,
                        "Parameters in query not found in method signature: ${queryVarsNotSubstituted.joinToString()}",
                        enclosing, method)
                return this
            }
        }else {
            add("$rawQueryVarName.bindToPreparedStmt(_stmt, _db, _stmt.getConnection())\n")
        }

        val resultSet: ResultSet?
        val execStmt: Statement?
        try {
            execStmt = dbConnection?.createStatement()

            if(isUpdateOrDelete) {
                //This can't be. An update will not be done using a RawQuery (that would just be done using execSQL)
                if(querySql == null)
                    throw IllegalStateException("QuerySql cannot be null")

                /*
                 Run this query now so that we would get an exception if there is something wrong with it.
                 */
                execStmt?.executeUpdate(querySql.replaceQueryNamedParamsWithDefaultValues(queryVars))
                add("val _numUpdates = _stmt.")
                if(suspended) {
                    add("%M()\n", MemberName("com.ustadmobile.door.jdbc.ext", "executeUpdateAsyncKmp"))
                }else {
                    add("executeUpdate()\n")
                }

                val entityModified = findEntityModifiedByQuery(querySql, allKnownEntityNames)

                beginControlFlow("if(_numUpdates > 0)")
                        .add("_db.%M(listOf(%S))\n", MEMBERNAME_HANDLE_TABLES_CHANGED, entityModified)
                        .endControlFlow()

                if(resultType != UNIT) {
                    add("$resultVarName = _numUpdates\n")
                }
            }else {
                if(suspended) {
                    beginControlFlow("_stmt.%M().%M",
                        MEMBERNAME_ASYNC_QUERY, MEMBERNAME_RESULTSET_USERESULTS)
                }else {
                    beginControlFlow("_stmt.executeQuery().%M",
                        MEMBERNAME_RESULTSET_USERESULTS)
                }

                add(" _resultSet ->\n")

                val colNames = mutableListOf<String>()
                if(querySql != null) {
                    resultSet = execStmt?.executeQuery(querySql.replaceQueryNamedParamsWithDefaultValues(queryVars))
                    val metaData = resultSet!!.metaData
                    for(i in 1 .. metaData.columnCount) {
                        colNames.add(metaData.getColumnName(i))
                    }
                }

                val entityVarName = "_entity"

                if(entityType !in QUERY_SINGULAR_TYPES && rawQueryVarName != null) {
                    add("val _columnIndexMap = _resultSet.%M()\n",
                        MemberName("com.ustadmobile.door.ext", "columnIndexMap"))
                }


                if(resultType.isListOrArray()) {
                    beginControlFlow("while(_resultSet.next())")
                }else {
                    beginControlFlow("if(_resultSet.next())")
                }

                if(QUERY_SINGULAR_TYPES.contains(entityType)) {
                    add("val $entityVarName = _resultSet.get${entityType.preparedStatementSetterGetterTypeName}(1)\n")
                }else {
                    add(resultEntityField!!.createSetterCodeBlock(rawQuery = rawQueryVarName != null,
                            colIndexVarName = "_columnIndexMap"))
                }

                if(resultType.isListOrArray()) {
                    add("$resultVarName.add(_entity)\n")
                }else {
                    add("$resultVarName = _entity\n")
                }

                endControlFlow()
                endControlFlow() //end use of resultset
            }
        }catch(e: SQLException) {
            logMessage(Diagnostic.Kind.ERROR,
                    "Exception running query SQL '$querySql' : ${e.message}",
                    enclosing = enclosing, element = method,
                    annotation = method?.annotationMirrors?.firstOrNull {it.annotationType.asTypeName() == Query::class.asTypeName()})
        }

        endControlFlow()

        return this
    }

    /**
     * Generate a JDBC insert code block. Generates an EntityInsertionAdapter, insert SQL,
     * and code that will insert from the given parameters
     *
     * @param parameterSpec - ParameterSpec representing the entity type to insert. This could be
     * any POKO with the Entity annotation, or a list thereof
     * @param returnType - TypeName representing the return value. This can be UNIT for no return type,
     * a long for a singular insert (return auto generated primary key), or a list of longs (return
     * all generated primary keys)
     * @param daoTypeBuilder The TypeBuilder being used to construct the DAO. If not already present,
     * an entity insertion adapter member variable will be added to the typeBuilder.
     * @param upsertMode - if true, the query will be generated as an upsert
     * @param addReturnStmt - if true, a return statement will be added to the codeblock, where the
     * return type will match the given returnType
     */
    @Deprecated("This should not be used anymore. This is handled in the refactored JDBC generator. It will be removed when sync is updated")
    fun generateInsertCodeBlock(parameterSpec: ParameterSpec, returnType: TypeName,
                                   entityTypeSpec: TypeSpec,
                                   daoTypeBuilder: TypeSpec.Builder,
                                   upsertMode: Boolean = false,
                                   addReturnStmt: Boolean = true,
                                   pgOnConflict: String? = null): CodeBlock {
        val codeBlock = CodeBlock.builder()
        val paramType = parameterSpec.type
        val entityClassName = paramType.asComponentClassNameIfList()

        val pgOnConflictHash = pgOnConflict?.hashCode()?.let { Math.abs(it) }?.toString() ?: ""
        val entityInserterPropName = "_insertAdapter${entityTypeSpec.name}_${if(upsertMode) "upsert" else ""}$pgOnConflictHash"
        if(!daoTypeBuilder.propertySpecs.any { it.name == entityInserterPropName }) {
            val fieldNames = mutableListOf<String>()
            val parameterHolders = mutableListOf<String>()

            val bindCodeBlock = CodeBlock.builder()
            var fieldIndex = 1
            val pkProp = entityTypeSpec.propertySpecs
                    .first { it.annotations.any { it.className == PrimaryKey::class.asClassName()} }

            entityTypeSpec.propertySpecs.forEach { prop ->
                fieldNames.add(prop.name)
                val pkAnnotation = prop.annotations.firstOrNull { it.className == PrimaryKey::class.asClassName() }
                val setterMethodName = prop.type.preparedStatementSetterGetterTypeName
                if(pkAnnotation != null && pkAnnotation.members.findBooleanMemberValue("autoGenerate") ?: false) {
                    parameterHolders.add("\${when(_db.jdbcDbType) { DoorDbType.POSTGRES -> " +
                            "\"COALESCE(?,nextval('${entityTypeSpec.name}_${prop.name}_seq'))\" else -> \"?\"} }")
                    bindCodeBlock.add("when(entity.${prop.name}){ ${defaultVal(prop.type)} " +
                            "-> stmt.setObject(${fieldIndex}, null) " +
                            "else -> stmt.set$setterMethodName(${fieldIndex++}, entity.${prop.name})  }\n")
                }else {
                    parameterHolders.add("?")
                    bindCodeBlock.add("stmt.set$setterMethodName(${fieldIndex++}, entity.${prop.name})\n")
                }
            }

            val statementClause = if(upsertMode) {
                "\${when(_db.jdbcDbType) { DoorDbType.SQLITE -> \"INSERT·OR·REPLACE\" else -> \"INSERT\"} }"
            }else {
                "INSERT"
            }

            val upsertSuffix = if(upsertMode) {
                val nonPkFields = entityTypeSpec.propertySpecs
                        .filter { ! it.annotations.any { it.className == PrimaryKey::class.asClassName() } }
                val nonPkFieldPairs = nonPkFields.map { "${it.name}·=·excluded.${it.name}" }
                val pkField = entityTypeSpec.propertySpecs
                        .firstOrNull { it.annotations.any { it.className == PrimaryKey::class.asClassName()}}
                val pgOnConflictVal = pgOnConflict?.replace(" ", "·") ?: "ON·CONFLICT·(${pkField?.name})·" +
                    "DO·UPDATE·SET·${nonPkFieldPairs.joinToString(separator = ",·")}"
                "\${when(_db.jdbcDbType){ DoorDbType.POSTGRES -> \"$pgOnConflictVal\" " +
                        "else -> \"·\" } } "
            } else {
                ""
            }

            val autoGenerateSuffix = " \${when{ _db.jdbcDbType == DoorDbType.POSTGRES && returnsId -> " +
                    "\"·RETURNING·${pkProp.name}·\"  else -> \"\"} } "

            val sql = """
                $statementClause INTO ${entityTypeSpec.name} (${fieldNames.joinToString()})
                VALUES (${parameterHolders.joinToString()})
                $upsertSuffix
                $autoGenerateSuffix
                """.trimIndent()

            val insertAdapterSpec = TypeSpec.anonymousClassBuilder()
                    .superclass(EntityInsertionAdapter::class.asClassName().parameterizedBy(entityClassName))
                    .addSuperclassConstructorParameter("_db")
                    .addFunction(FunSpec.builder("makeSql")
                            .addParameter("returnsId", BOOLEAN)
                            .addModifiers(KModifier.OVERRIDE)
                            .addCode("return \"\"\"%L\"\"\"", sql).build())
                    .addFunction(FunSpec.builder("bindPreparedStmtToEntity")
                            .addModifiers(KModifier.OVERRIDE)
                            .addParameter("stmt", CLASSNAME_PREPARED_STATEMENT)
                            .addParameter("entity", entityClassName)
                            .addCode(bindCodeBlock.build()).build())

            daoTypeBuilder.addProperty(PropertySpec.builder(entityInserterPropName,
                    EntityInsertionAdapter::class.asClassName().parameterizedBy(entityClassName))
                    .initializer("%L", insertAdapterSpec.build())
                    .build())
        }



        if(returnType != UNIT) {
            codeBlock.add("val _retVal = ")
        }


        val insertMethodName = makeInsertAdapterMethodName(paramType, returnType)
        codeBlock.add("$entityInserterPropName.$insertMethodName(${parameterSpec.name})")

        if(returnType != UNIT) {
            if(returnType.isListOrArray()
                    && returnType is ParameterizedTypeName
                    && returnType.typeArguments[0] == INT) {
                codeBlock.add(".map { it.toInt() }")
            }else if(returnType == INT){
                codeBlock.add(".toInt()")
            }
        }

        codeBlock.add("\n")

        codeBlock.add("_db.%M(listOf(%S))\n", MEMBERNAME_HANDLE_TABLES_CHANGED, entityTypeSpec.name)

        if(addReturnStmt) {
            if(returnType != UNIT) {
                codeBlock.add("return _retVal")
            }

            if(returnType is ParameterizedTypeName
                    && returnType.rawType == ARRAY) {
                codeBlock.add(".toTypedArray()")
            }else if(returnType == LongArray::class.asClassName()) {
                codeBlock.add(".toLongArray()")
            }else if(returnType == IntArray::class.asClassName()) {
                codeBlock.add(".toIntArray()")
            }
        }

        codeBlock.add("\n")

        return codeBlock.build()
    }

    /**
     * Given a TypeSpec for an abstract DAO class. At present this is only generating query
     * functions but could be extended.
     */
    fun generateJdbcDaoImpl(daoTypeSpec: TypeSpec, implClassName: String, pkgName: String): TypeSpec {
        val implTypeSpec =jdbcDaoTypeSpecBuilder(implClassName, ClassName(pkgName, daoTypeSpec.name!!))

        daoTypeSpec.funSpecs.forEach {funSpec ->
            if(funSpec.annotations. any { it.className == Query::class.asClassName() }) {
                val queryAnnotation = funSpec.annotations.first { it.className == Query::class.asClassName()}

                val overridingFun = funSpec.toBuilder()
                        .addModifiers(KModifier.OVERRIDE)
                        .addCode(CodeBlock.builder().addJdbcQueryCode(funSpec.returnType ?: UNIT,
                                funSpec.parameters.map { it.name to it.type}.toMap(),
                                queryAnnotation.memberToString(), null, null)
                            .build())
                if(funSpec.returnType != UNIT) {
                    overridingFun.addCode("return _result\n")
                }

                overridingFun.annotations.clear()
                overridingFun.modifiers.remove(KModifier.ABSTRACT)

                implTypeSpec.addFunction(overridingFun.build())
            }
        }

        return implTypeSpec.build()
    }




    fun logMessage(kind: Diagnostic.Kind, message: String, enclosing: TypeElement? = null,
                   element: Element? = null, annotation: AnnotationMirror? = null) {
        val messageStr = "DoorDb: ${enclosing?.qualifiedName}#${element?.simpleName} $message "
        if(annotation != null && element != null) {
            messager.printMessage(kind, messageStr, element, annotation)
        }else if(element != null) {
            messager.printMessage(kind, messageStr, element)
        }else {
            messager.printMessage(kind, messageStr)
        }
    }

    /**
     * Write the given file spec to directories specified in the annotation processor argument. Paths
     * should be separated by the path separator character (platform dependent - e.g. : on Unix, ; on Windows)
     */
    protected fun writeFileSpecToOutputDirs(fileSpec: FileSpec, argName: String, useFilerAsDefault: Boolean = true) {
        (processingEnv.options[argName]?.split(File.pathSeparator) ?: listOf(processingEnv.options["kapt.kotlin.generated"]!!)).forEach {
            fileSpec.writeTo(File(it))
        }
    }

    /**
     * Write the given FileSpec to the directories specified in the annotation processor arguments.
     * Paths should be separated by the path separator character (platform dependent - e.g. :
     * on Unix, ; on Windows)
     */
    protected fun FileSpec.writeToDirsFromArg(argName: String, useFilerAsDefault: Boolean = true) {
        writeToDirsFromArg(listOf(argName), useFilerAsDefault)
    }

    protected fun FileSpec.writeToDirsFromArg(argNames: List<String>, useFilerAsDefault: Boolean = true) {
        val outputArgDirs = argNames.flatMap {argName ->
            processingEnv.options[argName]?.split(File.pathSeparator)
                    ?: if(useFilerAsDefault) { listOf("filer") } else { listOf() }
        }

        outputArgDirs.forEach {
            val outputPath = if(it == "filer") {
                processingEnv.options["kapt.kotlin.generated"]!!
            }else {
                it
            }

            writeTo(File(outputPath))
        }
    }

    companion object {
        val CLASSNAME_CONNECTION = ClassName("com.ustadmobile.door.jdbc", "Connection")

        val CLASSNAME_PREPARED_STATEMENT = ClassName("com.ustadmobile.door.jdbc", "PreparedStatement")

        val CLASSNAME_STATEMENT = ClassName("com.ustadmobile.door.jdbc", "Statement")

        val CLASSNAME_SQLEXCEPTION = ClassName("com.ustadmobile.door.jdbc", "SQLException")

        val CLASSNAME_DATASOURCE = ClassName("com.ustadmobile.door.jdbc", "DataSource")

        val CLASSNAME_EXCEPTION = ClassName("kotlin", "Exception")

        val CLASSNAME_RUNTIME_EXCEPTION = ClassName("kotlin", "RuntimeException")

        val CLASSNAME_ILLEGALARGUMENTEXCEPTION = ClassName("kotlin", "IllegalArgumentException")

        val MEMBERNAME_ASYNC_QUERY = MemberName("com.ustadmobile.door.jdbc.ext", "executeQueryAsyncKmp")

        val MEMBERNAME_RESULTSET_USERESULTS = MemberName("com.ustadmobile.door.ext", "useResults")

        val MEMBERNAME_MUTABLE_LINKEDLISTOF = MemberName("com.ustadmobile.door.ext", "mutableLinkedListOf")

        val MEMBERNAME_PREPARE_AND_USE_STMT_ASYNC = MemberName("com.ustadmobile.door.ext",
            "prepareAndUseStatementAsync")

        val MEMBERNAME_PREPARE_AND_USE_STMT = MemberName("com.ustadmobile.door.ext", "prepareAndUseStatement")

        internal fun prepareAndUseStatmentMemberName(suspended: Boolean) = if(suspended)
            MEMBERNAME_PREPARE_AND_USE_STMT_ASYNC
        else
            MEMBERNAME_PREPARE_AND_USE_STMT

        const val SUFFIX_DEFAULT_RECEIVEVIEW = "_ReceiveView"

        val MEMBERNAME_HANDLE_TABLES_CHANGED = MemberName("com.ustadmobile.door.ext", "handleTablesChanged")

        const val PGSECTION_COMMENT_PREFIX = "/*psql"

        val MEMBERNAME_EXEC_UPDATE_ASYNC = MemberName("com.ustadmobile.door.jdbc.ext", "executeUpdateAsyncKmp")
    }

}