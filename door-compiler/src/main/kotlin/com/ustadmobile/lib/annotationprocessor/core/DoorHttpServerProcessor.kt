package com.ustadmobile.lib.annotationprocessor.core

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import com.ustadmobile.door.AbstractDoorUriResponder
import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.DoorDaoProvider
import com.ustadmobile.door.NanoHttpdCall
import com.ustadmobile.door.annotation.*
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.http.DbAndDao
import com.ustadmobile.door.http.DoorHttpServerConfig
import com.ustadmobile.door.http.DoorJsonRequest
import com.ustadmobile.door.http.DoorJsonResponse
import com.ustadmobile.door.ktor.KtorCallDaoAdapter
import com.ustadmobile.door.ktor.KtorCallDbAdapter
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.replication.DoorReplicationEntity
import com.ustadmobile.door.replication.DoorRepositoryReplicationClient
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.CALL_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.CODEBLOCK_KTOR_NO_CONTENT_RESPOND
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.CODEBLOCK_NANOHTTPD_NO_CONTENT_RESPONSE
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.DI_INSTANCE_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.DI_INSTANCE_TYPETOKEN_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.DI_ON_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.GET_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.POST_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.RESPOND_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.SERVER_TYPE_KTOR
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.SERVER_TYPE_NANOHTTPD
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.SUFFIX_KTOR_ROUTE
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.SUFFIX_NANOHTTPD_ADDURIMAPPING
import com.ustadmobile.lib.annotationprocessor.core.DoorHttpServerProcessor.Companion.SUFFIX_NANOHTTPD_URIRESPONDER
import com.ustadmobile.lib.annotationprocessor.core.ext.*
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import io.ktor.http.*
import io.ktor.server.routing.*
import org.kodein.di.DI

fun CodeBlock.Builder.addNanoHttpdResponse(varName: String, addNonNullOperator: Boolean = false,
                                           applyToResponseCodeBlock: CodeBlock? = null)
        = add("return %T.newFixedLengthResponse(%T.Status.OK, %T.MIME_TYPE_JSON, _gson.toJson($varName${if(addNonNullOperator) "!!" else ""}))\n",
            NanoHTTPD::class, NanoHTTPD.Response::class, DoorConstants::class)
        .takeIf { applyToResponseCodeBlock != null }
            ?.beginControlFlow(".apply ")
            ?.add(applyToResponseCodeBlock!!)
            ?.endControlFlow()

fun CodeBlock.Builder.addKtorResponse(varName: String)
        = add("%M.%M($varName)\n", CALL_MEMBER, RESPOND_MEMBER)


fun FunSpec.Builder.addParametersForHttpDb(isPrimaryDefaultVal: Boolean): FunSpec.Builder {
    addParameter(ParameterSpec.builder("_isPrimary", BOOLEAN)
                    .defaultValue(isPrimaryDefaultVal.toString())
                    .build())
    return this
}


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

fun FileSpec.Builder.addNanoHttpdResponder(
    daoKSClassDeclaration: KSClassDeclaration,
    resolver: Resolver,
    logger: KSPLogger,
): FileSpec.Builder {

    addType(TypeSpec.classBuilder(daoKSClassDeclaration.toClassNameWithSuffix(SUFFIX_NANOHTTPD_URIRESPONDER))
        .addOriginatingKSClass(daoKSClassDeclaration)
        .superclass(AbstractDoorUriResponder::class)
        .apply {
            daoKSClassDeclaration.getAllFunctions()
                .filter { it.hasAnnotation(HttpAccessible::class) }
                .forEach { daoFun ->
                    addNanoHttpDaoFun(daoFun, daoKSClassDeclaration, resolver, logger)
                }
        }
        .addNanoHttpdResponderFun("get", daoKSClassDeclaration.toClassName(), daoKSClassDeclaration)
        .addNanoHttpdResponderFun("post", daoKSClassDeclaration.toClassName(), daoKSClassDeclaration)
        .build())
    return this
}

/**
 *
 */
fun TypeSpec.Builder.addNanoHttpDaoFun(
    daoFunDecl: KSFunctionDeclaration,
    daoClassDecl: KSClassDeclaration,
    resolver: Resolver,
    logger: KSPLogger,
): TypeSpec.Builder {
    val daoFunSpec = daoFunDecl.toFunSpecBuilder(resolver, daoClassDecl.asType(emptyList()), logger).build()

    addFunction(FunSpec.builder(daoFunDecl.simpleName.asString())
        .returns(NanoHTTPD.Response::class)
        .addNanoHttpdUriResponderParams()
        .addParameter("_dao", daoClassDecl.toClassName())
        .addParameter("_gson", Gson::class)
        .addCode(CodeBlock.builder()
            .addHttpServerPassToDaoCodeBlock(daoFunSpec,
                serverType = SERVER_TYPE_NANOHTTPD)
            .build())
        .build())

    return this
}

/**
 * Add the parameters that are required for all nanohttpd uri responder functions
 */
private fun FunSpec.Builder.addNanoHttpdUriResponderParams() =
        addParameter("_uriResource", RouterNanoHTTPD.UriResource::class)
        .addParameter("_urlParams",
                Map::class.parameterizedBy(String::class, String::class))
        .addParameter("_session", NanoHTTPD.IHTTPSession::class)


/**
 * Add a function that overrides the get or post function of the NanoHTTPD
 * responder which will route the query to the appropriate function with
 * the required parameters (database, dao, gson, etc)
 */
fun TypeSpec.Builder.addNanoHttpdResponderFun(
    methodName: String,
    daoClassName: ClassName,
    daoClassDecl: KSClassDeclaration,
) : TypeSpec.Builder {

    val isPostFn = methodName.lowercase() == "post"

    val daoFunsToRespond = daoClassDecl.getAllFunctions().filter { daoFun ->
        daoFun.hasAnnotation(HttpAccessible::class)
    }.filter { daoFun ->
        val funResolved = daoFun.asMemberOf(daoClassDecl.asType(emptyList()))
        funResolved.parameterTypes.any { it?.toTypeName()?.isHttpQueryQueryParam() == false } == isPostFn
    }.toList()

    fun CodeBlock.Builder.addNanoHttpdReturnNotFound() : CodeBlock.Builder {
        add("%T.newFixedLengthResponse(%T.Status.NOT_FOUND, " +
                "%T.MIME_TYPE_PLAIN, %S)", NanoHTTPD::class, NanoHTTPD.Response::class,
                DoorConstants::class, "")
        return this
    }

    val daoParamNames = "_uriResource, _urlParams, _session, _dao, _gson"

    addFunction(FunSpec.builder(methodName)
            .addModifiers(KModifier.OVERRIDE)
            .returns(NanoHTTPD.Response::class)
            .addNanoHttpdUriResponderParams()
            .addCode(CodeBlock.builder()
                    .add("val _fnName = _session.uri.%M('/')\n", MemberName("kotlin.text", "substringAfterLast"))
                    .add("val _di = _uriResource.initParameter(0, %T::class.java)\n", DI::class)
                    .add("val _daoProvider = _uriResource.initParameter(1, %T::class.java) as %T\n",
                            DoorDaoProvider::class,
                            DoorDaoProvider::class.asClassName().parameterizedBy(
                                RoomDatabase::class.asClassName(), daoClassName))
                    .add("val _typeToken = _uriResource.initParameter(2, %T::class.java) as %T\n",
                        org.kodein.type.TypeToken::class.java,
                            org.kodein.type.TypeToken::class.parameterizedBy(RoomDatabase::class))
                    .add("val _call = %T(_uriResource, _urlParams, _session)\n", NanoHttpdCall::class)
                    .add("val _db: %T by _di.%M(_call).%M(_typeToken, tag = %T.TAG_DB)\n", RoomDatabase::class,
                            DI_ON_MEMBER, DI_INSTANCE_TYPETOKEN_MEMBER, DoorTag::class)
                    .add("val _repo: %T by _di.%M(_call).%M(_typeToken, tag = %T.TAG_REPO)\n", RoomDatabase::class,
                            DI_ON_MEMBER, DI_INSTANCE_TYPETOKEN_MEMBER, DoorTag::class)
                    .add("val _dao = _daoProvider.getDao(_db)\n")
                    .add("val _gson : %T by _di.%M()\n", Gson::class, DI_INSTANCE_MEMBER)
                    .apply {
                        if(daoFunsToRespond.isEmpty()) {
                            add("return ").addNanoHttpdReturnNotFound().add("\n")
                        }else {
                            beginControlFlow("return when(_fnName)")
                            daoFunsToRespond.forEach {
                                add("%S -> ${it.simpleName.asString()}($daoParamNames)\n", it.simpleName.asString())
                            }
                            add("else -> ").addNanoHttpdReturnNotFound().add("\n")
                            endControlFlow()
                        }
                    }
                    .build())
            .build()
    )


    return this
}


/**
 * Generates a Codeblock that will call the DAO method, and then call.respond with the result
 *
 * e.g.
 * val paramName = request.queryParameters['paramName']?.toLong()
 * val _result = _dao.methodName(paramName)
 * call.respond(_result)
 *
 * @param daoMethod FunSpec representing the method that is being delegated
 * @param preexistingVarNames a list of variable names that already exist in the scope being
 * generated. The name created in scope must be the variable name prefixed with __ e.g.
 * __paramName.
 *
 * This will skip generation of getting the parameter name from the call (e.g.
 * no val __paramName = request.queryParameters["paramName"] will be generated
 */
fun CodeBlock.Builder.addHttpServerPassToDaoCodeBlock(
    daoMethod: FunSpec,
    mutlipartHelperVarName: String? = null,
    beforeDaoCallCode: CodeBlock = CodeBlock.of(""),
    afterDaoCallCode: CodeBlock = CodeBlock.of(""),
    daoVarName: String = "_dao",
    preexistingVarNames: List<String> = listOf(),
    serverType: Int = SERVER_TYPE_KTOR,
    addRespondCall: Boolean = true
): CodeBlock.Builder {
    val getVarsCodeBlock = CodeBlock.builder()
    val callCodeBlock = CodeBlock.builder()

    val returnType = daoMethod.returnType
    if(returnType != UNIT) {
        callCodeBlock.add("val _result = ")
    }

    val useRunBlocking = serverType == SERVER_TYPE_NANOHTTPD
            && (KModifier.SUSPEND in daoMethod.modifiers)

    callCodeBlock.takeIf { useRunBlocking }?.beginControlFlow("%M",
            MemberName("kotlinx.coroutines", "runBlocking"))

    callCodeBlock.add("$daoVarName.${daoMethod.name}(")
    var paramOutCount = 0
    daoMethod.parameters.forEachIndexed { _, param ->
        val paramTypeName = param.type.javaToKotlinType()


        if(paramOutCount > 0)
            callCodeBlock.add(",")

        callCodeBlock.add("__${param.name}")

        if(param.name !in preexistingVarNames) {
            getVarsCodeBlock.add("val __${param.name} : %T = ", param.type)
                .addGetParamFromHttpRequest(paramTypeName, param.name,
                            multipartHelperVarName = mutlipartHelperVarName,
                            serverType = serverType)
                    .add("\n")
        }


        paramOutCount++
    }



    callCodeBlock.add(")\n")

    callCodeBlock.takeIf { useRunBlocking }?.endControlFlow()

    var respondResultType = returnType
    respondResultType = respondResultType.copy(nullable = respondResultType.isNullableAsSelectReturnResult)

    add(getVarsCodeBlock.build())
    add(beforeDaoCallCode)
    add(callCodeBlock.build())

    add(afterDaoCallCode)
    if(addRespondCall) {
        addRespondCall(respondResultType, "_result", serverType)
    }

    return this
}

fun CodeBlock.Builder.addGetParamFromHttpRequest(typeName: TypeName, paramName: String,
                                         declareVariableName: String? = null,
                                         declareVariableType: String = "val",
                                         gsonVarName: String = "_gson",
                                         multipartHelperVarName: String? = null,
                                         serverType: Int = SERVER_TYPE_KTOR): CodeBlock.Builder {

    if(declareVariableName != null) {
        add("%L %L =", declareVariableType, declareVariableName)
    }

    if(typeName.isHttpQueryQueryParam()) {
        if(typeName in QUERY_SINGULAR_TYPES) {
            if(serverType == SERVER_TYPE_KTOR) {
                add("%M.request.queryParameters[%S]", CALL_MEMBER, paramName)
            }else {
                add("_session.parameters.get(%S)?.get(0)", paramName)
            }
            if(typeName == String::class.asTypeName()) {
                add(" ?: \"\"")
            }else {
                add("?.to${(typeName as ClassName).simpleName}() ?: ${typeName.defaultTypeValueCode()}")
            }
        }else {
            if(serverType == SERVER_TYPE_KTOR) {
                add("%M.request.queryParameters.getAll(%S)", CALL_MEMBER,
                        paramName)
            }else {
                add("_session.parameters[%S]", paramName)
            }

            val parameterizedTypeName = typeName as ParameterizedTypeName
            if(parameterizedTypeName.typeArguments[0] != String::class.asClassName()) {
                add("·?.map·{·it.to${(parameterizedTypeName.typeArguments[0] as ClassName).simpleName}()·}")
            }
            add("·?:·listOf()\n")
        }
    }else {
        val getJsonStrCodeBlock = if(multipartHelperVarName != null) {
            CodeBlock.of("$multipartHelperVarName.receiveJsonStr()")
        }else if(serverType == SERVER_TYPE_KTOR){
            CodeBlock.of("%M.%M<String>()", CALL_MEMBER,
                    MemberName("io.ktor.server.request", "receiveOrNull"))
        }else {
            CodeBlock.of("mutableMapOf<String,String>().also{_session.parseBody(it)}.get(%S)",
                    "postData")
        }
        add("$gsonVarName.fromJson(")
        add(getJsonStrCodeBlock)
        add(", object: %T() {}.type)",
                TypeToken::class.asClassName().parameterizedBy(removeTypeProjection(typeName)))
    }

    if(declareVariableName != null){
        add("\n")
    }

    return this
}

fun CodeBlock.Builder.addRespondCall(returnType: TypeName, varName: String, serverType: Int = SERVER_TYPE_KTOR,
                        ktorBeforeRespondCodeBlock: CodeBlock? = null,
                        nanoHttpdApplyCodeBlock: CodeBlock? = null): CodeBlock.Builder {

    if(ktorBeforeRespondCodeBlock != null && serverType == SERVER_TYPE_KTOR)
        add(ktorBeforeRespondCodeBlock)

    when{
        returnType == UNIT && serverType == SERVER_TYPE_KTOR->
            add(CODEBLOCK_KTOR_NO_CONTENT_RESPOND)

        returnType == UNIT && serverType == SERVER_TYPE_NANOHTTPD ->
            add(CODEBLOCK_NANOHTTPD_NO_CONTENT_RESPONSE)

        !returnType.isNullableAsSelectReturnResult && serverType == SERVER_TYPE_KTOR->
            addKtorResponse(varName)

        !returnType.isNullableAsSelectReturnResult && serverType == SERVER_TYPE_NANOHTTPD ->
            addNanoHttpdResponse(varName, applyToResponseCodeBlock = nanoHttpdApplyCodeBlock)


        else -> beginControlFlow("if($varName != null)")
                .apply {
                    takeIf { serverType == SERVER_TYPE_KTOR }?.addKtorResponse(varName
                    )
                    takeIf { serverType == SERVER_TYPE_NANOHTTPD }?.addNanoHttpdResponse(varName,
                            addNonNullOperator = true, applyToResponseCodeBlock = nanoHttpdApplyCodeBlock)
                }
                .nextControlFlow("else")
                .apply {
                    takeIf { serverType == SERVER_TYPE_KTOR }
                            ?.add(CODEBLOCK_KTOR_NO_CONTENT_RESPOND)
                    takeIf { serverType == SERVER_TYPE_NANOHTTPD }
                            ?.add(CODEBLOCK_NANOHTTPD_NO_CONTENT_RESPONSE)
                }
                .endControlFlow()
    }

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

/**
 * Add a NanoHTTPD mapper function for the database (maps all DAOs for this database to RouterNanoHTTPD)
 * to this FileSpec.
 */
fun FileSpec.Builder.addDbNanoHttpdMapperFunction(
    dbClassDeclaration: KSClassDeclaration,
) : FileSpec.Builder {
    val dbTypeClassName = dbClassDeclaration.toClassName()
    addFunction(FunSpec.builder("${dbTypeClassName.simpleName}$SUFFIX_NANOHTTPD_ADDURIMAPPING")
        .addOriginatingKSClass(dbClassDeclaration)
        .addParametersForHttpDb(false)
        .addParameter("_mappingPrefix", String::class)
        .addParameter("_di", DI::class)
        .receiver(RouterNanoHTTPD::class)
        .addCode(CodeBlock.builder()
            .add("val _typeToken : %T = %M()\n",
                org.kodein.type.TypeToken::class.asClassName().parameterizedBy(dbTypeClassName),
                MemberName("org.kodein.type", "erased"))
            .apply {
                dbClassDeclaration.dbEnclosedDaos().filter {
                    it.hasAnnotation(Repository::class)
                }.forEach { daoClassDecl ->
                    val daoPropOrGetter = dbClassDeclaration.findDbGetterForDao(daoClassDecl)
                    add("addRoute(\"\$_mappingPrefix/${daoClassDecl.simpleName.asString()}/.*\",\n " +
                            "%T::class.java, _di,\n %T(){ it.${daoPropOrGetter?.toPropertyOrEmptyFunctionCaller()} }, _typeToken",
                        daoClassDecl.toClassNameWithSuffix(SUFFIX_NANOHTTPD_URIRESPONDER),
                        DoorDaoProvider::class.asTypeName().parameterizedBy(
                            dbTypeClassName, daoClassDecl.toClassName()))
                    add(")\n")
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
                        add("json.decodeFromString(")
                        addKotlinxSerializationStrategy(funAsMemberOfDao.parameterTypes[index]!!, resolver)
                        if(param.hasAnnotation(RepoHttpBodyParam::class)) {
                            add(", request.requireBodyAsString())\n")
                        }else {
                            add(", request.requireParam(%S))\n", param.name?.asString())
                        }
                    }

                    if(daoFunDecl.returnType?.resolve()?.isPagingSource() == true) {
                        add("val _pagingLoadParams = request.").addGetRequestPagingSourceLoadParams(resolver)
                            .add("\n")
                    }

                    val httpAccessibleAnnotation = daoFunDecl.getAnnotation(HttpAccessible::class)
                    httpAccessibleAnnotation?.authQueries?.forEach { authQuery ->
                        val authFun = daoKSClassDeclaration.getAllFunctions().first {
                            it.simpleName.asString() == authQuery.functionName
                        }
                        add("if(!")
                        addHttpServerFunctionCallCode(
                            funDeclaration = authFun,
                            httpAccessibleFunctionCalls = httpAccessibleAnnotation.authQueries.toList(),
                            pagingLoadParamValName = "_pagingLoadParams",
                            requestValName = "request",
                            resolver = resolver
                        )
                        beginControlFlow(")")
                        add("return %T.newErrorResponse(403)\n", DoorJsonResponse::class)
                        endControlFlow()
                    }

                    if(effectiveStrategy == HttpAccessible.ClientStrategy.PULL_REPLICATE_ENTITIES) {
                        addHttpReplicationEntityServerExtension(resolver, daoFunDecl, daoKSClassDeclaration,
                                requestValName = "request", pagingLoadParamValName = "_pagingLoadParams")
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
    httpAccessibleFunctionCalls: List<HttpServerFunctionCall>,
    requestValName: String,
    pagingLoadParamValName: String,
    resolver: Resolver,

) : CodeBlock.Builder {
    val functionCallAnnotation = httpAccessibleFunctionCalls.firstOrNull {
        it.functionName == funDeclaration.simpleName.asString()
    }
    val returnType = funDeclaration.returnType?.resolve()

    add("${funDeclaration.simpleName.asString()}(\n")
    withIndent {
        funDeclaration.parameters.forEach { param ->
            val paramNameStr = param.name?.asString()
            val argParamAnnotation = functionCallAnnotation?.functionArgs?.firstOrNull { it.name == paramNameStr }
            if(argParamAnnotation != null) {
                when(argParamAnnotation.argType) {
                    HttpServerFunctionParam.ArgType.LITERAL -> {
                        if(param.type.resolve().makeNotNullable() == resolver.builtIns.stringType)
                            add("$paramNameStr = %S,\n", argParamAnnotation.literalValue)
                        else
                            add("$paramNameStr = %L,\n", argParamAnnotation.literalValue)
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
        add(".%M(_pagingLoadParams)",
            MemberName("com.ustadmobile.door.paging", "loadPageDataOrEmptyList"))
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
    beginControlFlow("val replicationEntities = %M<%T>",
        MemberName("kotlin.collections", "buildList"),
        DoorReplicationEntity::class
    )

    //Next: we can add list of functions e.g. replicateData = ["selectAllSalesPeople", "selectAllSalesByType"]
    // that are going to generate data to replicate
    val httpAccessibleAnnotation = daoFunDecl.getAnnotation(HttpAccessible::class)
        ?: throw IllegalArgumentException("addHttpReplicationEntityServerExtension: can only be used for function with @HttpAccessible annotation")

    val functionsToReplicate = if(httpAccessibleAnnotation.pullQueriesToReplicate.isEmpty()) {
        listOf(daoFunDecl)
    }else {
        httpAccessibleAnnotation.pullQueriesToReplicate.map { daoFunCall ->
            daoKSClassDeclaration.getAllFunctions().first { it.simpleName.asString() == daoFunCall.functionName }
        }
    }

    functionsToReplicate.forEach { funDeclaration ->
        val returnType = funDeclaration.returnType?.resolve()
        val resultType = returnType?.unwrapResultType(resolver)
        val resultComponentType = resultType?.unwrapComponentTypeIfListOrArray(resolver)
        val resultValName = "_result_${funDeclaration.simpleName.asString()}"

        add("val $resultValName = ")
        addHttpServerFunctionCallCode(
            funDeclaration = funDeclaration,
            httpAccessibleFunctionCalls = httpAccessibleAnnotation.pullQueriesToReplicate.toList(),
            requestValName = requestValName,
            pagingLoadParamValName = pagingLoadParamValName,
            resolver = resolver,
        ).add("\n")

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
    add("headers = listOf(%T.HEADER_NODE_ID to _thisNodeId.toString()),\n", DoorConstants::class)
    add("bodyText = json.encodeToString(\n")
    indent()
    add("%T.serializer(),\n", DoorMessage::class)
    add("%T(\n", DoorMessage::class)
    indent()
    add("what = %T.WHAT_REPLICATION,\n", DoorMessage::class)
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

            DoorTarget.ANDROID -> {
                dbSymbols.forEach { dbClassDecl ->
                    if (dbClassDecl.dbEnclosedDaos().any { it.hasAnnotation(Repository::class) }) {
                        FileSpec.builder(dbClassDecl.packageName.asString(),
                            "${dbClassDecl.simpleName.asString()}$SUFFIX_NANOHTTPD_ADDURIMAPPING")
                            .addDbNanoHttpdMapperFunction(dbClassDecl)
                            .build()
                            .writeTo(environment.codeGenerator, false)
                    }
                }

                daoSymbols.forEach { daoClassDecl ->
                    FileSpec.builder(daoClassDecl.packageName.asString(), "${daoClassDecl.simpleName.asString()}$SUFFIX_NANOHTTPD_URIRESPONDER")
                        .addNanoHttpdResponder(daoClassDecl, resolver, environment.logger)
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

        const val SUFFIX_NANOHTTPD_URIRESPONDER = "_UriResponder"

        const val SUFFIX_NANOHTTPD_ADDURIMAPPING = "_AddUriMapping"

        const val SUFFIX_HTTP_SERVER_EXTENSION_FUNS = "_HttpServerExt"

        val GET_MEMBER = MemberName("io.ktor.server.routing", "get")

        val POST_MEMBER = MemberName("io.ktor.server.routing", "post")

        val CALL_MEMBER = MemberName("io.ktor.server.application", "call")

        val RESPOND_MEMBER = MemberName("io.ktor.server.response", "respond")

        val DI_ON_MEMBER = MemberName("org.kodein.di", "on")

        val DI_INSTANCE_MEMBER = MemberName("org.kodein.di", "instance")

        val DI_INSTANCE_TYPETOKEN_MEMBER = MemberName("org.kodein.di", "Instance")

        const val SERVER_TYPE_KTOR = 1

        const val SERVER_TYPE_NANOHTTPD = 2

        internal val CODEBLOCK_NANOHTTPD_NO_CONTENT_RESPONSE = CodeBlock.of(
            "return %T.newFixedLengthResponse(%T.Status.NO_CONTENT, %T.MIME_TYPE_PLAIN, %S)\n",
            NanoHTTPD::class, NanoHTTPD.Response::class, DoorConstants::class, "")

        internal val CODEBLOCK_KTOR_NO_CONTENT_RESPOND = CodeBlock.of("%M.%M(%T.NoContent, %S)\n",
            CALL_MEMBER, RESPOND_MEMBER, HttpStatusCode::class, "")

    }
}

