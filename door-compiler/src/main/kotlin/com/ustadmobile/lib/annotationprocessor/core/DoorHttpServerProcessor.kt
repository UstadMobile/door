package com.ustadmobile.lib.annotationprocessor.core

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.annotation.*
import com.ustadmobile.door.http.DbAndDao
import com.ustadmobile.door.http.DoorHttpServerConfig
import com.ustadmobile.door.http.DoorJsonRequest
import com.ustadmobile.door.http.DoorJsonResponse
import com.ustadmobile.door.ktor.KtorCallDaoAdapter
import com.ustadmobile.door.ktor.KtorCallDbAdapter
import com.ustadmobile.door.log.DoorLogLevel
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.replication.DoorReplicationEntity
import com.ustadmobile.door.replication.DoorRepositoryReplicationClient
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.CALL_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.END_OF_PAGINATION_REACHED_VALNAME
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.GET_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.POST_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.SUFFIX_KTOR_ROUTE
import com.ustadmobile.lib.annotationprocessor.core.ext.*
import io.ktor.server.routing.*


/**
 * Adds a KTOR Route function for the given DAO to the FileSpec
 *
 * fun Route.DaoName_KtorRoute(
 *   serverConfig: DoorHttpServerConfig,
 *   callAdapter: KtorCallDaoAdapter,
 * ) {
 *    get("functionName") {
 *        val daoAndDb = callAdapter(call)
 *        call.respondDoorJson(
 *            daoAndDb.dao.functionName_DoorHttp(serverConfig, call.request.toDoorJsonRequest()
 *        )
 *    }
 * }
 */
fun FileSpec.Builder.addDaoKtorRouteFun(
    daoClassDecl: KSClassDeclaration,
    daoClassName: ClassName,
) : FileSpec.Builder {

    addFunction(FunSpec.builder("${daoClassDecl.simpleName.asString()}$SUFFIX_KTOR_ROUTE")
        .addAnnotation(AnnotationSpec.builder(Suppress::class)
            .addMember("%S, %S, %S, %S", "LocalVariableName", "RedundantSuppression", "FunctionName", "RedundantVisibilityModifier")
            .build())
        .addOriginatingKSClass(daoClassDecl)
        .receiver(Route::class)
        .addParameter("serverConfig", DoorHttpServerConfig::class)
        .addParameter("daoCallAdapter", KtorCallDaoAdapter::class.asClassName().parameterizedBy(daoClassName))
        .addCode(CodeBlock.builder()
        .apply {
            daoClassDecl.getAllFunctions().filter {
                it.hasAnnotation(HttpAccessible::class)
            }.forEach {funDecl ->
                val httpMethodMember = if(funDecl.getDaoFunHttpMethodToUse() == "GET") {
                    GET_MEMBER
                }else {
                    POST_MEMBER
                }

                beginControlFlow("%M(%S)", httpMethodMember, funDecl.simpleName.asString())
                add("val _daoAndDb = daoCallAdapter(call)\n")
                add("%M.%M(\n", CALL_MEMBER, MemberName("com.ustadmobile.door.ktor", "respondDoorJson"))
                indent()
                add(
                    "_daoAndDb.dao.%M(serverConfig, %M.%M(_daoAndDb.db))\n",
                    MemberName(daoClassDecl.packageName.asString(), "${funDecl.simpleName.asString()}_DoorHttp"),
                    CALL_MEMBER,
                    MemberName("com.ustadmobile.door.ktor", "toDoorRequest")
                )
                unindent()
                add(")\n")
                endControlFlow()
            }
        }
        .build())
    .build())


    return this
}




/**
 * Adds a Ktor Route function that will subroute all the DAOs on the given database
 */
fun FileSpec.Builder.addDbKtorRouteFunction(
    dbClassDeclaration: KSClassDeclaration,
) : FileSpec.Builder {

    val dbClassName = dbClassDeclaration.toClassName()
    addFunction(FunSpec.builder("${dbClassName.simpleName}$SUFFIX_KTOR_ROUTE")
        .addOriginatingKSClass(dbClassDeclaration)
        .receiver(Route::class)
        .addParameter("serverConfig", DoorHttpServerConfig::class)
        .addParameter("dbCallAdapter", KtorCallDbAdapter::class.asClassName().parameterizedBy(
            dbClassName
        ))
        .addCode(CodeBlock.builder()
            .apply {
                dbClassDeclaration.getAnnotation(MinReplicationVersion::class)?.also {
                    add("%M(${it.value})\n",
                        MemberName("com.ustadmobile.door.ktor",
                            "addDbVersionCheckIntercept"))
                }

                if(dbClassDeclaration.hasAnnotation(DoorNodeIdAuthRequired::class)) {
                    add("%M()\n", MemberName("com.ustadmobile.door.ktor", "addNodeIdAndAuthCheckInterceptor"))
                }

                beginControlFlow("%M(%T.REPLICATION_PATH)",
                    MemberName("io.ktor.server.routing", "route"),
                    DoorRepositoryReplicationClient::class,
                )
                add("%M(serverConfig, dbCallAdapter)\n",
                    MemberName("com.ustadmobile.door.ktor.routes", "ReplicationRoute")
                )
                endControlFlow()

                dbClassDeclaration.dbEnclosedDaos().filter {daoKsClass ->
                    daoKsClass.getAllFunctions().any { it.hasAnnotation(HttpAccessible::class) }
                }.forEach { daoKsClass ->
                    beginControlFlow("%M(%S)",
                        MemberName("io.ktor.server.routing", "route"),
                        daoKsClass.simpleName.asString()
                    )

                    add("%M(\n", MemberName(daoKsClass.packageName.asString(),
                        "${daoKsClass.simpleName.asString()}$SUFFIX_KTOR_ROUTE"))
                    indent()
                    add("serverConfig = serverConfig,\n")
                    beginControlFlow("daoCallAdapter = ")
                    add("call -> ")
                    add("dbCallAdapter(call).let { %T(it, it.%L) }\n",
                        DbAndDao::class.asClassName().parameterizedBy(daoKsClass.toClassName()),
                        dbClassDeclaration.findDbGetterForDao(daoKsClass)?.toPropertyOrEmptyFunctionCaller()
                    )
                    endControlFlow()
                    unindent()
                    add(")\n")

                    endControlFlow()
                }
            }
            .build())
        .build())

    return this
}

fun CodeBlock.Builder.addGetRequestPagingSourceLoadParams(
    resolver: Resolver,
): CodeBlock.Builder {
    add("%M(\n",
        MemberName("com.ustadmobile.door.ext", "requirePagingSourceLoadParams"))
    withIndent {
        add("json = json,\n")
        add("keyDeserializationStrategy = ").addKotlinxSerializationStrategy(
            resolver.builtIns.intType.makeNullable(), resolver
        ).add(",\n")
    }
    add(")\n")
    return this
}

/**
 * Where the receiver KSFunctionDeclaration that represents a DAO function, return the function that will determine
 * if the end of pagination has been reached.
 *
 *
 * @return null if this function does not return a PagingSource or otherwise have a function that determines if the end
 *         of pagination has been reached.
 */
fun KSFunctionDeclaration.daoEndOfPaginationReachedFunction(): KSFunctionDeclaration? {
    if(returnType?.resolve()?.isPagingSource() != true)
        return null

    return this
}


fun FileSpec.Builder.addHttpServerExtensionFun(
    resolver: Resolver,
    daoKSClassDeclaration: KSClassDeclaration,
    daoFunDecl: KSFunctionDeclaration
): FileSpec.Builder {
    val effectiveStrategy = daoFunDecl.getDaoFunHttpAccessibleEffectiveStrategy(resolver)

    //Should add originating ks class for all entities used here.
    val funAsMemberOfDao = daoFunDecl.asMemberOf(daoKSClassDeclaration.asType(emptyList()))

    addFunction(
        FunSpec.builder(daoFunDecl.simpleName.asString() + "_DoorHttp")
            .addModifiers(KModifier.SUSPEND)
            .addAnnotation(AnnotationSpec.builder(Suppress::class)
                .addMember("%S, %S, %S, %S", "LocalVariableName", "RedundantSuppression", "FunctionName", "RedundantVisibilityModifier")
                .build())
            .addOriginatingKSClass(daoKSClassDeclaration)
            .receiver(daoKSClassDeclaration.toClassName())
            .returns(DoorJsonResponse::class)
            .addParameter("serverConfig", DoorHttpServerConfig::class)
            .addParameter("request", DoorJsonRequest::class)
            .addCode(CodeBlock.builder()
                .add("val json = serverConfig.json\n")
                .apply {
                    daoFunDecl.parameters.forEachIndexed { index, param ->
                        add("val _arg_${param.name?.asString()} : %T = ", funAsMemberOfDao.parameterTypes[index]?.toTypeName())

                        if(param.hasAnnotation(RepoHttpBodyParam::class)) {
                            add("request.bodyAsStringOrNull()")
                        }else {
                            add("request.queryParam(%S)", param.name?.asString())
                        }
                        beginControlFlow("?.let")
                        add("json.decodeFromString(")
                        addKotlinxSerializationStrategy(funAsMemberOfDao.parameterTypes[index]!!, resolver)
                        add(", it)\n")
                        unindent().add("}")

                        val paramTypeResolved = param.type.resolve()
                        if(!paramTypeResolved.isMarkedNullable) {
                            add(" ?: ")
                            add(param.type.resolve().defaultTypeValueCode(resolver))
                        }
                        add("\n")
                    }

                    if(daoFunDecl.returnType?.resolve()?.isPagingSource() == true) {
                        add("val _pagingLoadParams = request.").addGetRequestPagingSourceLoadParams(resolver)
                            .add("\n")
                    }

                    val httpAccessibleAnnotation = daoFunDecl.getKSAnnotationByType(HttpAccessible::class)
                    httpAccessibleAnnotation?.getArgumentValueByNameAsAnnotationList("authQueries")?.forEach { authQuery ->
                        val authFun = authQuery.httpServerFunctionFunctionDecl(daoKSClassDeclaration, resolver)
                        add("if(!")
                        addHttpServerFunctionCallCode(
                            funDeclaration = authFun,
                            httpAccessibleFunctionCall = authQuery,
                            pagingLoadParamValName = "_pagingLoadParams",
                            requestValName = "request",
                            resolver = resolver
                        )
                        beginControlFlow(")")
                        add("return %T.newErrorResponse(403)\n", DoorJsonResponse::class)
                        endControlFlow()
                    }

                    if(effectiveStrategy == HttpAccessible.ClientStrategy.PULL_REPLICATE_ENTITIES) {
                        addHttpReplicationEntityServerExtension(
                            resolver, daoFunDecl, daoKSClassDeclaration,
                            requestValName = "request", pagingLoadParamValName = "_pagingLoadParams"
                        )
                    }else {
                        //Run the query and return JSON
                        add("val _thisNodeId = request.db.%M\n",
                            MemberName("com.ustadmobile.door.ext", "doorWrapperNodeId"))
                        add("val _result = %L(", daoFunDecl.simpleName.asString())
                        daoFunDecl.parameters.forEach { param ->
                            add("_arg_${param.name?.asString()},")
                        }
                        add(")")
                        if(daoFunDecl.returnType?.resolve()?.isPagingSource() == true) {
                            val componentType = daoFunDecl.returnType?.resolve()?.unwrapResultType(resolver)
                                ?: throw IllegalArgumentException("Cannot determine result type for " +
                                        daoFunDecl.simpleName.asString())
                            add(".load(_pagingLoadParams)\n")
                            add("return _result.%M(",
                                MemberName("com.ustadmobile.door.ext", "toJsonResponse"))
                            withIndent {
                                add("json = json,\n")
                                add("keySerializer = ").addKotlinxSerializationStrategy(
                                    resolver.builtIns.intType.makeNullable(), resolver).add(",\n")
                                add("valueSerializer = ").addKotlinxSerializationStrategy(componentType, resolver)
                                    .add(",\n")
                                add("localNodeId = _thisNodeId,\n")
                            }
                            add(")\n")
                            //Add special return using extension function here.
                        }else {
                            add("\n")
                            add("return %T(", DoorJsonResponse::class)
                            withIndent {
                                add("\n")
                                val returnType = funAsMemberOfDao.returnType
                                if(returnType != null && returnType.toTypeName() != UNIT) {
                                    add("bodyText = json.encodeToString(")
                                    addKotlinxSerializationStrategy(returnType, resolver)
                                    add(", _result),\n")
                                }else {
                                    add("bodyText = %S,\n", "")
                                }
                                add("headers = listOf(%T.HEADER_NODE_ID to _thisNodeId.toString()),\n", DoorConstants::class)
                            }
                            add(")\n")
                        }


                    }
                }
                .build())
            .build()
    )
    return this
}


/**
 * Add a code block for the given function declaration which is to be executed as per an HttpServerCall
 */
fun CodeBlock.Builder.addHttpServerFunctionCallCode(
    funDeclaration: KSFunctionDeclaration,
    httpAccessibleFunctionCall: KSAnnotation?,
    requestValName: String,
    pagingLoadParamValName: String,
    resolver: Resolver,
    convertPagingSourceToList: Boolean = true,
) : CodeBlock.Builder {
    val returnType = funDeclaration.returnType?.resolve()

    httpAccessibleFunctionCall?.getArgumentValueByNameAsKSType("functionDao")
            ?.takeIf { it != resolver.builtIns.anyType }
            ?.also {daoClass ->
        add("$requestValName.db.%M.getDaoByClass(%T::class).",
            MemberName("com.ustadmobile.door.ext", "doorWrapper"),
            daoClass.toClassName()
        )
    }


    add("${funDeclaration.simpleName.asString()}(\n")
    withIndent {
        funDeclaration.parameters.forEach { param ->
            val paramNameStr = param.name?.asString()
            val argParamAnnotation = httpAccessibleFunctionCall?.getArgumentValueByNameAsAnnotationList("functionArgs")?.firstOrNull {
                it.getArgumentValueByNameAsString("name") == paramNameStr
            }
            val argType = argParamAnnotation?.getArgumentValueByNameAsKSType("argType")?.let {
                HttpServerFunctionParam.ArgType.valueOf(it.declaration.simpleName.asString())
            }

            if(argType != null) {
                when(argType) {
                    HttpServerFunctionParam.ArgType.LITERAL -> {
                        val literalValue = argParamAnnotation.getArgumentValueByNameAsString("literalValue")
                        if(param.type.resolve().makeNotNullable() == resolver.builtIns.stringType)
                            add("$paramNameStr = %S,\n", literalValue)
                        else
                            add("$paramNameStr = %L,\n", literalValue)
                    }
                    HttpServerFunctionParam.ArgType.REQUESTER_NODE_ID -> {
                        add("$paramNameStr = $requestValName.requireNodeId(),\n")
                    }
                    HttpServerFunctionParam.ArgType.PAGING_KEY -> {
                        add("$paramNameStr = $pagingLoadParamValName.key ?: 0,\n")
                    }
                    HttpServerFunctionParam.ArgType.PAGING_LOAD_SIZE -> {
                        add("$paramNameStr = $pagingLoadParamValName.loadSize,\n")
                    }
                    HttpServerFunctionParam.ArgType.MAP_OTHER_PARAM -> {
                        val otherParamName = argParamAnnotation.getArgumentValueByNameAsString("fromName")
                        add("$paramNameStr = _arg_${otherParamName},\n")
                    }
                    HttpServerFunctionParam.ArgType.PAGING_OFFSET ->{
                        add("$paramNameStr = %M($pagingLoadParamValName, ($pagingLoadParamValName.key ?: 0), Int.MAX_VALUE),\n",
                            MemberName("com.ustadmobile.door.paging", "getOffset"))
                    }
                    HttpServerFunctionParam.ArgType.PAGING_LIMIT -> {
                        add("$paramNameStr = %M($pagingLoadParamValName, ($pagingLoadParamValName.key ?: 0)),\n",
                            MemberName("com.ustadmobile.door.paging", "getLimit"))
                    }
                }
            }else {
                add("$paramNameStr = _arg_$paramNameStr,\n")
            }
        }
    }
    add(")")
    if(returnType?.isFlow() == true) {
        add(".%M()", MemberName("kotlinx.coroutines.flow", "first"))
    }else if(returnType?.isPagingSource() == true) {
        if(convertPagingSourceToList) {
            add(".%M(_pagingLoadParams)",
                MemberName("com.ustadmobile.door.paging", "loadPageDataOrEmptyList"))
        }
    }

    return this
}

fun CodeBlock.Builder.addHttpReplicationEntityServerExtension(
    resolver: Resolver,
    daoFunDecl: KSFunctionDeclaration,
    daoKSClassDeclaration: KSClassDeclaration,
    requestValName: String,
    pagingLoadParamValName: String,
) : CodeBlock.Builder {
    val httpAccessibleKSAnnotation = daoFunDecl.getKSAnnotationByType(HttpAccessible::class)
        ?: throw IllegalArgumentException("addHttpReplicationEntityServerExtension: can only be used for function with @HttpAccessible annotation")
    val queriesToReplicate = httpAccessibleKSAnnotation.getArgumentValueByNameAsAnnotationList("pullQueriesToReplicate")
        ?: emptyList()

    val functionsToReplicate = if(queriesToReplicate.isEmpty()) {
        listOf(daoFunDecl)
    }else {
        queriesToReplicate.map { queryToReplicate ->
            queryToReplicate.httpServerFunctionFunctionDecl(daoKSClassDeclaration, resolver)
        }
    }

    functionsToReplicate.forEach { funDeclaration ->
        val isPagingSource = funDeclaration.returnType?.resolve()?.isPagingSource() ?: false
        val resultBaseName = "_result_${funDeclaration.simpleName.asString()}"
        val resultSuffix = if(isPagingSource) {
            "_pagingSource"
        }else {
            ""
        }
        val resultValName = "$resultBaseName$resultSuffix"


        add("val $resultValName = ")
        addHttpServerFunctionCallCode(
            funDeclaration = funDeclaration,
            httpAccessibleFunctionCall = queriesToReplicate.firstOrNull {
                it.getArgumentValueByNameAsString("functionName") == funDeclaration.simpleName.asString()
            },
            requestValName = requestValName,
            pagingLoadParamValName = pagingLoadParamValName,
            resolver = resolver,
            convertPagingSourceToList = false
        )

        if(isPagingSource) {
            add(".%M($pagingLoadParamValName)\n", MemberName("com.ustadmobile.door.paging", "loadPageDataForHttp"))
            add("val $resultBaseName = $resultValName.data\n")

            if(
                funDeclaration == daoFunDecl.daoEndOfPaginationReachedFunction()
            ) {
                add("serverConfig.logger.log(%T.VERBOSE, \"%L\")\n", DoorLogLevel::class,
                    "DoorPaging:·loaded·from:·\${_pagingLoadParams.key}·endOfPaginationReached=\${$resultValName.endOfPaginationReached}")
                add("val $END_OF_PAGINATION_REACHED_VALNAME = $resultValName.endOfPaginationReached\n")
            }
        }
        add("\n")
    }


    beginControlFlow("val replicationEntities = %M<%T>",
        MemberName("kotlin.collections", "buildList"),
        DoorReplicationEntity::class
    )

    functionsToReplicate.forEach { funDeclaration ->
        val returnType = funDeclaration.returnType?.resolve()
        val resultType = returnType?.unwrapResultType(resolver)
        val resultComponentType = resultType?.unwrapComponentTypeIfListOrArray(resolver)
        val resultValName = "_result_${funDeclaration.simpleName.asString()}"

        val replicationEntitiesOnResult = (resultComponentType?.declaration as? KSClassDeclaration)
            ?.getDoorReplicateEntityComponents()

        if(resultType?.isListOrArrayType(resolver) == true) {
            replicationEntitiesOnResult?.forEach { replicateEntityAndPath ->
                add("addAll(\n")
                indent()
                val propertyIsNullable = replicateEntityAndPath.propertyPathIsNullable
                val mapFunName = if(propertyIsNullable) "mapNotNull" else "map"
                beginControlFlow("$resultValName.$mapFunName")
                add("_row ->\n")
                addCreateDoorReplicationCodeBlock(
                    entityKSClass = replicateEntityAndPath.entity,
                    entityNullable = replicateEntityAndPath.propertyPathIsNullable,
                    entityValName = replicateEntityAndPath.propertyPathFrom("_row"),
                    jsonVarName = "json",
                )
                add("\n") // new line won't be automatically added by addCreateDoorReplicationCodeBlock
                endControlFlow()
                unindent()
                add(")\n")
            }
        }else {
            replicationEntitiesOnResult?.forEach { replicateEntityAndPath ->
                val isNullable = resultType.isMarkedNullable || replicateEntityAndPath.propertyPathIsNullable
                var effectiveValName = replicateEntityAndPath.propertyPathFrom(resultValName)
                if(isNullable) {
                    beginControlFlow(replicateEntityAndPath.propertyPathFrom(
                        baseValName = resultValName,
                        fromNullable = true
                    ) + "?.also")
                    add("_row -> \n")
                    effectiveValName = "_row"
                }

                add("add(\n")
                indent()
                addCreateDoorReplicationCodeBlock(
                    entityKSClass = replicateEntityAndPath.entity,
                    entityNullable = false,
                    entityValName = effectiveValName,
                    jsonVarName = "json"
                )
                unindent()
                add("\n)\n")

                if(isNullable) {
                    endControlFlow()
                }
            }
        }
    }


    endControlFlow()

    add("val _thisNodeId = request.db.%M\n",
        MemberName("com.ustadmobile.door.ext", "doorWrapperNodeId"))
    add("return %T(\n", DoorJsonResponse::class)
    indent()
    beginControlFlow("headers = buildList")
    add("add(Pair(%T.HEADER_NODE_ID, _thisNodeId.toString()))\n", DoorConstants::class)
    if(daoFunDecl.daoEndOfPaginationReachedFunction() != null) {
        add("add(Pair(%T.HEADER_PAGING_END_REACHED, $END_OF_PAGINATION_REACHED_VALNAME.toString()))\n",
            DoorConstants::class)
    }
    endControlFlow()
    add(",\n")
    add("bodyText = json.encodeToString(\n")
    indent()
    add("%T.serializer(),\n", DoorMessage::class)
    add("%T(\n", DoorMessage::class)
    indent()
    add("what = %T.WHAT_REPLICATION_PULL,\n", DoorMessage::class)
    add("fromNode = _thisNodeId,\n")
    add("toNode = request.requireNodeId(),\n")
    add("replications = replicationEntities,\n")
    unindent()
    add(")\n")//end DoorMessage constructor
    unindent()
    add(")\n")//end json.encodetoString
    unindent()
    add(")\n")//end DoorJsonResponse constructor

    return this
}


/**
 * This annotation processor generates a KTOR Route for each DAO, and a KTOR Route for each DAO
 * with subroutes for each DAO that is part of the database.
 *
 * Each Syncable KTOR DAO will have the following additional interfaces and classes generated:
 *
 * - DaoName_KtorHelper : This is an interface with a method for each syncable select query. These
 *  methods use refactored SQL that avoids sending a client entities it was already sent. The
 *  generated method will have a clientId parameter (to filter by), and offset and limit parameters
 *  (if the DAO itself returns a DataSource.Factory). The return type will always be the plain entity
 *  itself or a list thereof (not Flow or PagingSource).
 *
 * - DaoName_KtorHelperLocal: Abstract class implementing DaoName_KtorHelper using the local change
 *  sequence number as the basis for filtering entities. A JDBC implementation of this DAO will also
 *  be generated.
 *
 * - DaoName_KtorHelperMaster: Abstract class implementing DaoName_KtorHelper using the master change
 *  sequence number as the basis for filtering entities. A JDBC implementation of this DAO will also
 *  be generated.
 *
 */
class DoorHttpServerProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {


    override fun process(resolver: Resolver): List<KSAnnotated> {
        val dbSymbols = resolver.getDatabaseSymbolsToProcess()

        val daoSymbols = resolver.getDaoSymbolsToProcess()
            .filter { it.hasAnnotation(Repository::class) }

        val target = environment.doorTarget(resolver)

        when(target) {
            DoorTarget.JVM -> {
                dbSymbols.forEach { dbClassDecl ->
                    if (dbClassDecl.dbEnclosedDaos().any { it.hasAnnotation(Repository::class) }) {
                        FileSpec.builder(
                            dbClassDecl.packageName.asString(),
                            "${dbClassDecl.simpleName.asString()}$SUFFIX_KTOR_ROUTE")
                            .addDbKtorRouteFunction(dbClassDecl)
                            .build()
                            .writeTo(environment.codeGenerator, false)
                    }
                }

                daoSymbols.forEach { daoClassDecl ->
                    FileSpec.builder(daoClassDecl.packageName.asString(), "${daoClassDecl.simpleName.asString()}$SUFFIX_KTOR_ROUTE")
                        .addDaoKtorRouteFun(daoClassDecl, daoClassDecl.toClassName())
                        .build()
                        .writeTo(environment.codeGenerator, false)
                }
            }

            else -> {
                // do nothing
            }
        }

        daoSymbols.takeIf { target != DoorTarget.JS }?.forEach { daoDecl ->
            val httpAccessibleFunctions = daoDecl.getAllFunctions().filter { it.hasAnnotation(HttpAccessible::class) }.toList()
            if(httpAccessibleFunctions.isNotEmpty()) {
                FileSpec.builder(daoDecl.packageName.asString(), "${daoDecl.simpleName.asString()}$SUFFIX_HTTP_SERVER_EXTENSION_FUNS")
                    .apply {
                        httpAccessibleFunctions.forEach { daoFun ->
                            addHttpServerExtensionFun(resolver, daoDecl, daoFun)
                        }
                    }
                    .build()
                    .writeTo(environment.codeGenerator, false)
            }

        }

        return emptyList()
    }

    companion object {

        const val SUFFIX_KTOR_ROUTE = "_KtorRoute"

        const val SUFFIX_HTTP_SERVER_EXTENSION_FUNS = "_HttpServerExt"

        const val END_OF_PAGINATION_REACHED_VALNAME = "_endOfPaginationReached"

        val GET_MEMBER = MemberName("io.ktor.server.routing", "get")

        val POST_MEMBER = MemberName("io.ktor.server.routing", "post")

        val CALL_MEMBER = MemberName("io.ktor.server.application", "call")

    }
}

