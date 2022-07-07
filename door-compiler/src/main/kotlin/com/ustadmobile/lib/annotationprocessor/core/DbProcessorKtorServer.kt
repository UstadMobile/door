package com.ustadmobile.lib.annotationprocessor.core

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Database
import androidx.room.RoomDatabase
import com.google.gson.Gson
import com.squareup.kotlinpoet.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import javax.lang.model.element.TypeElement
import com.google.gson.reflect.TypeToken
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.*
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_KTOR_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SERVER_TYPE_KTOR
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SERVER_TYPE_NANOHTTPD
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import java.util.*
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.CODEBLOCK_KTOR_NO_CONTENT_RESPOND
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.CODEBLOCK_NANOHTTPD_NO_CONTENT_RESPONSE
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.DI_INSTANCE_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.DI_INSTANCE_TYPETOKEN_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.DI_ON_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.GET_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.POST_MEMBER
import org.kodein.di.DI
import java.lang.IllegalArgumentException
import javax.annotation.processing.ProcessingEnvironment
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_ANDROID_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_NANOHTTPD_URIRESPONDER
import com.ustadmobile.door.AbstractDoorUriResponder
import com.ustadmobile.door.annotation.*
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_ROUTE
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_NANOHTTPD_ADDURIMAPPING
import kotlinx.serialization.json.Json
import javax.annotation.processing.RoundEnvironment

fun CodeBlock.Builder.addNanoHttpdResponse(varName: String, addNonNullOperator: Boolean = false,
                                           applyToResponseCodeBlock: CodeBlock? = null)
        = add("return %T.newFixedLengthResponse(%T.Status.OK, %T.MIME_TYPE_JSON, _gson.toJson($varName${if(addNonNullOperator) "!!" else ""}))\n",
            NanoHTTPD::class, NanoHTTPD.Response::class, DoorConstants::class)
        .takeIf { applyToResponseCodeBlock != null }
            ?.beginControlFlow(".apply ")
            ?.add(applyToResponseCodeBlock!!)
            ?.endControlFlow()

fun CodeBlock.Builder.addKtorResponse(varName: String)
        = add("%M.%M($varName)\n", DbProcessorKtorServer.CALL_MEMBER,
            DbProcessorKtorServer.RESPOND_MEMBER)


fun FunSpec.Builder.addParametersForHttpDb(dbTypeElement: TypeElement, isPrimaryDefaultVal: Boolean): FunSpec.Builder {
    addParameter(ParameterSpec.builder("_isPrimary", BOOLEAN)
                    .defaultValue(isPrimaryDefaultVal.toString())
                    .build())
    return this
}


fun CodeBlock.Builder.addRequestDi(diVarName: String = "_di", dbVarName: String = "_db",
    typeTokenVarName: String = "_typeToken", serverType: Int = SERVER_TYPE_KTOR) : CodeBlock.Builder {
    if(serverType == SERVER_TYPE_KTOR) {
        add("val ${diVarName} = %M()\n", MemberName("org.kodein.di.ktor", "closestDI"))
                .add("val $dbVarName : %T = %M($typeTokenVarName)\n",
                    TypeVariableName.invoke("T"),
                    MemberName("com.ustadmobile.door.ext", "unwrappedDbOnCall"))
    }

    return this
}

/**
 * Adds a KTOR Route function for the given DAO to the FileSpec
 *
 * e.g. route("DaoName") {
 * ...
 * }
 */
fun FileSpec.Builder.addDaoKtorRouteFun(
    daoTypeSpec: TypeSpec,
    daoClassName: ClassName,
    processingEnv: ProcessingEnvironment
) : FileSpec.Builder {

    addFunction(FunSpec.builder("${daoTypeSpec.name}$SUFFIX_KTOR_ROUTE")
            .addTypeVariable(TypeVariableName.invoke("T", RoomDatabase::class))
            .receiver(Route::class)
            .addParameter("_typeToken",
                    org.kodein.type.TypeToken::class.asClassName().parameterizedBy(TypeVariableName("T")))
            .addParameter("_daoFn",
                    LambdaTypeName.get(parameters = arrayOf(TypeVariableName("T")),
                            returnType = daoClassName))
            .addCode(CodeBlock.builder()
                    .beginControlFlow("%M(%S)",
                        MemberName("io.ktor.server.routing", "route"),
                        daoClassName.simpleName)
                    .apply {
                        daoTypeSpec.funSpecs.specsWithHttpEndpoint().forEach {daoFunSpec ->
                            addKtorDaoMethodCode(daoFunSpec, processingEnv)
                        }
                    }
                    .endControlFlow()
                    .build())
            .build())


    return this
}

/**
 * Add a NanoHTTPD Responder class implementation for the DAO to this file
 */
fun FileSpec.Builder.addNanoHttpdResponder(daoTypeSpec: TypeSpec, daoClassName: ClassName,
    processingEnv: ProcessingEnvironment): FileSpec.Builder {

    addType(TypeSpec.classBuilder(daoClassName.withSuffix(SUFFIX_NANOHTTPD_URIRESPONDER))
            .superclass(AbstractDoorUriResponder::class)
            .apply {
                //generate a function for each dao function
                daoTypeSpec.funSpecs.specsWithHttpEndpoint().forEach { daoFunSpec ->
                    addNanoHttpdDaoFun(daoFunSpec, daoClassName, daoTypeSpec,
                        processingEnv)
                }

                addNanoHttpdResponderFun("get", daoClassName, daoTypeSpec, processingEnv)
                addNanoHttpdResponderFun("post", daoClassName, daoTypeSpec, processingEnv)
            }
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

fun TypeSpec.Builder.addNanoHttpdDaoFun(daoFunSpec: FunSpec, daoClassName: ClassName,
                                        daoTypeSpec: TypeSpec,
                                        processingEnv: ProcessingEnvironment) {

    addFunction(FunSpec.builder(daoFunSpec.name)
            .returns(NanoHTTPD.Response::class)
            .addNanoHttpdUriResponderParams()
            .addParameter("_dao", daoClassName)
            .addParameter("_gson", Gson::class)

            .addCode(CodeBlock.builder()
                    .addHttpServerPassToDaoCodeBlock(daoFunSpec, processingEnv,
                        serverType = SERVER_TYPE_NANOHTTPD)
                .build())
            .build())
}


/**
 * Add a function that overrides the get or post function of the NanoHTTPD
 * responder which will route the query to the appropriate function with
 * the required parameters (database, dao, gson, etc)
 */
fun TypeSpec.Builder.addNanoHttpdResponderFun(
    methodName: String,
    daoClassName: ClassName,
    daoTypeSpec: TypeSpec,
    @Suppress("UNUSED_PARAMETER")
    processingEnv: ProcessingEnvironment
) : TypeSpec.Builder {

    val isPostFn = methodName.lowercase() == "post"
    val daoFunsToRespond =  daoTypeSpec.funSpecs.specsWithHttpEndpoint().filter {
        it.httpBodyParams().isNotEmpty() == isPostFn
    }

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
                                add("%S -> ${it.name}($daoParamNames)\n", it.name)
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
 * Get a list of parameters of the FunSpec that must be transmitted in the http body (eg. entities)
 */
private fun FunSpec.httpBodyParams(): List<ParameterSpec> {
    return parameters.filter { !it.type.isHttpQueryQueryParam() }
}

fun CodeBlock.Builder.addKtorDaoMethodCode(daoFunSpec: FunSpec, processingEnv: ProcessingEnvironment) : CodeBlock.Builder {
    val memberFn = if(daoFunSpec.httpBodyParams().isNotEmpty()) {
        POST_MEMBER
    }else {
        GET_MEMBER
    }


    beginControlFlow("%M(%S)", memberFn, daoFunSpec.name)
            .addRequestDi()
            .add("val _dao = _daoFn(_db)\n")
            .add("val _gson: %T by _di.%M()\n", Gson::class, DI_INSTANCE_MEMBER)

    addHttpServerPassToDaoCodeBlock(daoFunSpec, processingEnv, serverType = SERVER_TYPE_KTOR)


    endControlFlow()

    return this
}

/**
 * Generates a CodeBlock for running an SQL select statement on the KTOR serer and then returning
 * the result as JSON.
 *
 * e.g.
 * get("methodName") {
 *   val paramVal = request.queryParameters['uid']?.toLong()
 *   .. query execution code (as per JDBC)
 *   call.respond(_result)
 * }
 *
 * The method will automatically choose between using get or post, and will use post if there
 * are any parameters which cannot be sent as query parameters (e.g. JSON), or get otherwise.
 *
 * This will handle refactoring the query to remove syncable entities already delivered to the
 * client making the request
 *
 * @param daoMethod A FunSpec representing the DAO method that this CodeBlock is being generated for
 * @param daoTypeEl The DAO element that this is being generated for: optional for error logging purposes
 *
 */
fun CodeBlock.Builder.addKtorRouteSelectCodeBlock(
    daoMethod: FunSpec,
    processingEnv: ProcessingEnvironment,
    serverType: Int = SERVER_TYPE_KTOR
) : CodeBlock.Builder {

    val returnTypeName = daoMethod.returnType
            ?: throw IllegalArgumentException("addKtorRouteSelectCodeBlock for ${daoMethod.name}: has null return type")

    val resultType = returnTypeName.unwrapLiveDataOrDataSourceFactory()
    val isDataSourceFactory = returnTypeName.isDataSourceFactory()

    val queryVarsList = daoMethod.parameters.toMutableList()
    if(isDataSourceFactory){
        queryVarsList += ParameterSpec.builder(PARAM_NAME_OFFSET, INT).build()
        queryVarsList += ParameterSpec.builder(PARAM_NAME_LIMIT, INT).build()
    }

    if(serverType != SERVER_TYPE_NANOHTTPD)
        //This is already a parameter in the nanohttpd function
        add("val _ktorHelperDao = _ktorHelperDaoFn(_db)\n")

    val modifiedQueryFunSpec = FunSpec.builder(daoMethod.name)
            .addParameters(queryVarsList)
            .returns(resultType)
    modifiedQueryFunSpec.takeIf { KModifier.SUSPEND in daoMethod.modifiers }
            ?.addModifiers(KModifier.SUSPEND)

    addHttpServerPassToDaoCodeBlock(modifiedQueryFunSpec.build(),
            processingEnv, daoVarName = "_ktorHelperDao",
            preexistingVarNames = listOf("clientId"), serverType = serverType, addRespondCall = false)

    addRespondCall(resultType, "_result", serverType)

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
    processingEnv: ProcessingEnvironment,
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

    val returnType = daoMethod.returnType ?: UNIT
    if(returnType != UNIT) {
        callCodeBlock.add("val _result = ")
    }

    val isLiveData = returnType is ParameterizedTypeName
            && returnType.rawType == LiveData::class.asClassName()
    val useRunBlocking = serverType == SERVER_TYPE_NANOHTTPD
            && (KModifier.SUSPEND in daoMethod.modifiers || isLiveData)

    callCodeBlock.takeIf { useRunBlocking }?.beginControlFlow("%M",
            MemberName("kotlinx.coroutines", "runBlocking"))

    callCodeBlock.add("$daoVarName.${daoMethod.name}(")
    var paramOutCount = 0
    daoMethod.parameters.forEachIndexed {index, param ->
        val paramTypeName = param.type.javaToKotlinType()
        if(isContinuationParam(paramTypeName))
            return@forEachIndexed

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



    callCodeBlock.add(")")
    if(isLiveData) {
        callCodeBlock.add(".%M()",
                MemberName("com.ustadmobile.door", "getFirstValue"))
    }
    callCodeBlock.add("\n")

    callCodeBlock.takeIf { useRunBlocking }?.endControlFlow()

    var respondResultType = returnType.unwrapLiveDataOrDataSourceFactory()
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
                add("%M.request.queryParameters[%S]", DbProcessorKtorServer.CALL_MEMBER, paramName)
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
                add("%M.request.queryParameters.getAll(%S)", DbProcessorKtorServer.CALL_MEMBER,
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
            CodeBlock.of("%M.%M<String>()", DbProcessorKtorServer.CALL_MEMBER,
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
    dbTypeEl: TypeElement,
    processingEnv: ProcessingEnvironment
) : FileSpec.Builder {

    fun CodeBlock.Builder.addDbDaoRouteCall(
        daoTypeEl: TypeElement
    ) : CodeBlock.Builder {
        add("%M(\n_typeToken, \n", MemberName(daoTypeEl.packageName,
                "${daoTypeEl.simpleName}$SUFFIX_KTOR_ROUTE"))
        beginControlFlow("")
                .add("it.%L\n", dbTypeEl.findDaoGetter(daoTypeEl, processingEnv))
                .endControlFlow()

        add(")\n\n")
        return this
    }



    val dbClassName = dbTypeEl.asClassName()
    addFunction(FunSpec.builder("${dbClassName.simpleName}$SUFFIX_KTOR_ROUTE")
            .receiver(Route::class)
            .addParameter(ParameterSpec.builder("json", Json::class)
                .defaultValue(CodeBlock.of("%T { encodeDefaults = true } ", Json::class))
                .build())
            .addCode(CodeBlock.builder()
                    .apply {
                        dbTypeEl.getAnnotation(MinReplicationVersion::class.java)?.also {
                            add("%M(${it.value})\n",
                                    MemberName("com.ustadmobile.door.ktor",
                                            "addDbVersionCheckIntercept"))
                        }

                        if(dbTypeEl.getAnnotation(DoorNodeIdAuthRequired::class.java) != null) {
                            add("%M()\n", MemberName("com.ustadmobile.door.ktor", "addNodeIdAndAuthCheckInterceptor"))
                        }

                        add("val _typeToken: %T<%T> = %M()\n", org.kodein.type.TypeToken::class.java,
                                dbTypeEl, DbProcessorKtorServer.DI_ERASED_MEMBER)
                        if(dbTypeEl.dbHasReplicateWrapper(processingEnv)) {
                            add("%M(_typeToken, %T::class, json)\n",
                                MemberName("com.ustadmobile.door.replication", "doorReplicationRoute"),
                                dbTypeEl)
                        }
                    }.apply {
                        dbTypeEl.allDbClassDaoGettersWithRepo(processingEnv).forEach {daoGetter ->
                            val daoTypeEl = daoGetter.returnType.asTypeElement(processingEnv)
                                    ?: throw IllegalArgumentException("${daoGetter.simpleName} has no return type?")
                            addDbDaoRouteCall(daoTypeEl)
                        }
                    }.applyIf(dbTypeEl.allDbEntities(processingEnv).any { it.entityHasAttachments }) {
                        add("%M(%S, _typeToken)\n", MemberName("com.ustadmobile.door.attachments",
                                "doorAttachmentsRoute"), "attachments")
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
    dbTypeElement: TypeElement,
    processingEnv: ProcessingEnvironment
) : FileSpec.Builder {

    val dbTypeClassName = dbTypeElement.asClassName()
    addFunction(FunSpec.builder("${dbTypeClassName.simpleName}$SUFFIX_NANOHTTPD_ADDURIMAPPING")
            .addParametersForHttpDb(dbTypeElement, false)
            .addParameter("_mappingPrefix", String::class)
            .addParameter("_di", DI::class)
            .receiver(RouterNanoHTTPD::class)
            .addCode(CodeBlock.builder()
                    .add("val _typeToken : %T = %M()\n",
                            org.kodein.type.TypeToken::class.asClassName().parameterizedBy(dbTypeClassName),
                            MemberName("org.kodein.type", "erased"))
                    .apply {
                        dbTypeElement.allDbClassDaoGettersWithRepo(processingEnv).forEach { daoGetter ->
                            val daoTypeEl = daoGetter.returnType.asTypeElement(processingEnv)
                                    ?: throw IllegalArgumentException("${daoGetter.simpleName} has no return type?")
                            add("addRoute(\"\$_mappingPrefix/${daoTypeEl.simpleName}/.*\",\n " +
                                    "%T::class.java, _di,\n %T(){ it.${daoGetter.accessAsPropertyOrFunctionInvocationCall()} }, _typeToken",
                                    daoTypeEl.asClassNameWithSuffix(SUFFIX_NANOHTTPD_URIRESPONDER),
                                    DoorDaoProvider::class.asTypeName().parameterizedBy(
                                        dbTypeClassName, daoTypeEl.asClassName()))
                            add(")\n")
                        }
                    }
                    .build())
            .build())


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
 *  itself or a list thereof (not LiveData or DataSource.Factory).
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


class DbProcessorKtorServer: AbstractDbProcessor() {



    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        roundEnv.getElementsAnnotatedWith(Database::class.java).map { it as TypeElement}.forEach {dbTypeEl ->
            if(dbTypeEl.dbEnclosedDaos(processingEnv).any { it.hasAnnotation(Repository::class.java) }) {
                FileSpec.builder(dbTypeEl.packageName, "${dbTypeEl.simpleName}$SUFFIX_KTOR_ROUTE")
                        .addDbKtorRouteFunction(dbTypeEl, processingEnv)
                        .build()
                        .writeToDirsFromArg(OPTION_KTOR_OUTPUT)
                FileSpec.builder(dbTypeEl.packageName, "${dbTypeEl.simpleName}$SUFFIX_NANOHTTPD_ADDURIMAPPING")
                        .addDbNanoHttpdMapperFunction(dbTypeEl, processingEnv)
                        .build()
                        .writeToDirsFromArg(OPTION_ANDROID_OUTPUT)
            }
        }


        val daos = roundEnv.getElementsAnnotatedWith(Dao::class.java)
        daos.filter { it is TypeElement && it.isDaoWithRepository }.map { it as TypeElement }.forEach {daoTypeEl ->
            val daoTypeSpec = daoTypeEl.asTypeSpecStub(processingEnv)

            FileSpec.builder(daoTypeEl.packageName, "${daoTypeEl.simpleName}$SUFFIX_KTOR_ROUTE")
                    .addDaoKtorRouteFun(daoTypeSpec, daoTypeEl.asClassName(), processingEnv)
                    .build()
                    .writeToDirsFromArg(OPTION_KTOR_OUTPUT)

            FileSpec.builder(daoTypeEl.packageName, "${daoTypeEl.simpleName}$SUFFIX_NANOHTTPD_URIRESPONDER")
                    .addNanoHttpdResponder(daoTypeSpec, daoTypeEl.asClassName(), processingEnv)
                    .build()
                    .writeToDirsFromArg(OPTION_ANDROID_OUTPUT)
        }


        return true
    }


    companion object {

        const val SUFFIX_KTOR_ROUTE = "_KtorRoute"

        const val SUFFIX_KTOR_HELPER = "_KtorHelper"

        const val SUFFIX_KTOR_HELPER_MASTER = "_KtorHelperMaster"

        const val SUFFIX_KTOR_HELPER_LOCAL = "_KtorHelperLocal"

        const val SUFFIX_NANOHTTPD_URIRESPONDER = "_UriResponder"

        const val SUFFIX_NANOHTTPD_ADDURIMAPPING = "_AddUriMapping"

        val GET_MEMBER = MemberName("io.ktor.server.routing", "get")

        val POST_MEMBER = MemberName("io.ktor.server.routing", "post")

        val CALL_MEMBER = MemberName("io.ktor.server.application", "call")

        val RESPOND_MEMBER = MemberName("io.ktor.server.response", "respond")

        val RESPONSE_HEADER = MemberName("io.ktor.server.response", "header")

        val DI_ON_MEMBER = MemberName("org.kodein.di", "on")

        val DI_INSTANCE_MEMBER = MemberName("org.kodein.di", "instance")

        val DI_INSTANCE_TYPETOKEN_MEMBER = MemberName("org.kodein.di", "Instance")

        val DI_ERASED_MEMBER = MemberName("org.kodein.type", "erased")

        const val SERVER_TYPE_KTOR = 1

        const val SERVER_TYPE_NANOHTTPD = 2

        internal val CODEBLOCK_NANOHTTPD_NO_CONTENT_RESPONSE = CodeBlock.of(
                "return %T.newFixedLengthResponse(%T.Status.NO_CONTENT, %T.MIME_TYPE_PLAIN, %S)\n",
                NanoHTTPD::class, NanoHTTPD.Response::class, DoorConstants::class, "")

        internal val CODEBLOCK_KTOR_NO_CONTENT_RESPOND = CodeBlock.of("%M.%M(%T.NoContent, %S)\n",
                CALL_MEMBER, RESPOND_MEMBER, HttpStatusCode::class, "")

    }
}