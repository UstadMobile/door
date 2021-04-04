package com.ustadmobile.lib.annotationprocessor.core

import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Database
import androidx.room.Insert
import com.google.gson.Gson
import com.squareup.kotlinpoet.*
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
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
import com.ustadmobile.door.annotation.MinSyncVersion
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.CODEBLOCK_KTOR_NO_CONTENT_RESPOND
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.CODEBLOCK_NANOHTTPD_NO_CONTENT_RESPONSE
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.DI_INSTANCE_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.DI_INSTANCE_TYPETOKEN_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.DI_ON_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.GET_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.POST_MEMBER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_HELPER
import org.kodein.di.DI
import java.lang.IllegalArgumentException
import javax.annotation.processing.ProcessingEnvironment
import com.ustadmobile.door.annotation.MasterChangeSeqNum
import com.ustadmobile.door.annotation.LocalChangeSeqNum
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_ANDROID_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorJdbcKotlin.Companion.SUFFIX_JDBC_KT
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_HELPER_LOCAL
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_HELPER_MASTER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_NANOHTTPD_URIRESPONDER
import kotlin.reflect.KClass
import com.ustadmobile.door.AbstractDoorUriResponder
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_ROUTE
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_NANOHTTPD_ADDURIMAPPING
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorSync.Companion.SUFFIX_SYNCDAO_ABSTRACT
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorSync.Companion.SUFFIX_SYNCDAO_IMPL
import javax.annotation.processing.RoundEnvironment
import com.ustadmobile.door.annotation.SyncableLimitParam

fun CodeBlock.Builder.addNanoHttpdResponse(varName: String, addNonNullOperator: Boolean = false,
                                           applyToResponseCodeBlock: CodeBlock? = null)
        = add("return %T.newFixedLengthResponse(%T.Status.OK, %T.MIME_TYPE_JSON, _gson.toJson($varName${if(addNonNullOperator) "!!" else ""}))\n",
            NanoHTTPD::class, NanoHTTPD.Response::class, DoorConstants::class)
        .takeIf { applyToResponseCodeBlock != null }
            ?.beginControlFlow(".apply ")
            ?.add(applyToResponseCodeBlock!!)
            ?.endControlFlow()

fun CodeBlock.Builder.addKtorResponse(varName: String, addNonNullOperator: Boolean = false)
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
        add("val ${diVarName} = %M()\n", MemberName("org.kodein.di.ktor", "di"))
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
fun FileSpec.Builder.addDaoKtorRouteFun(daoTypeSpec: TypeSpec, daoClassName: ClassName,
                                        syncHelperClassName: ClassName = daoClassName.withSuffix("_SyncHelper"),
                                        processingEnv: ProcessingEnvironment)
        : FileSpec.Builder {


    addFunction(FunSpec.builder("${daoTypeSpec.name}${DbProcessorKtorServer.SUFFIX_KTOR_ROUTE}")
            .addTypeVariable(TypeVariableName.invoke("T", DoorDatabase::class))
            .receiver(Route::class)
            .addParameter("_typeToken",
                    org.kodein.type.TypeToken::class.asClassName().parameterizedBy(TypeVariableName("T")))
            .addParameter("_daoFn",
                    LambdaTypeName.get(parameters = *arrayOf(TypeVariableName("T")),
                            returnType = daoClassName))
            .apply {
                if(daoTypeSpec.daoSyncableEntitiesInSelectResults(processingEnv).isNotEmpty()) {
                    addParameter("_syncHelperFn",
                            LambdaTypeName.get(parameters = *arrayOf(TypeVariableName("T")),
                                    returnType = syncHelperClassName))
                    addParameter("_ktorHelperDaoFn",
                            LambdaTypeName.get(parameters = *arrayOf(TypeVariableName("T")),
                            returnType = daoClassName.withSuffix(SUFFIX_KTOR_HELPER)))
                }
            }
            .addCode(CodeBlock.builder()
                    .beginControlFlow("%M(%S)",
                        MemberName("io.ktor.routing", "route"),
                        daoClassName.simpleName)
                    .apply {
                        daoTypeSpec.funSpecs.forEach {daoFunSpec ->
                            addKtorDaoMethodCode(daoFunSpec, processingEnv)
                        }

                        daoTypeSpec.daoSyncableEntitiesInSelectResults(processingEnv).forEach {syncableEntity ->
                            val syncableEntityInfo = SyncableEntityInfo(syncableEntity, processingEnv)
                            addEntitiesAckKtorRoute(syncableEntityInfo.tracker, syncableEntity)
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
                daoTypeSpec.funSpecs.forEach { daoFunSpec ->
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
            .applyIf(daoTypeSpec.daoSyncableEntitiesInSelectResults(processingEnv).isNotEmpty()) {
                addParameter("_syncHelper", daoClassName.withSuffix("_SyncHelper"))
                addParameter("_ktorHelperDao", daoClassName.withSuffix(SUFFIX_KTOR_HELPER))
            }

            //If the dao function is a syncable insert, we need to repo as well so we can trigger the synclistener
            .applyIf(daoFunSpec.isSyncableInsert(processingEnv)) {
                addParameter("_repo", DoorDatabase::class)
            }
            .addCode(CodeBlock.builder()
                    .apply {
                        if(daoFunSpec.isQueryWithSyncableResults(processingEnv)) {
                            addKtorRouteSelectCodeBlock(daoFunSpec, processingEnv,
                                SERVER_TYPE_NANOHTTPD)
                        }else {
                            addHttpServerPassToDaoCodeBlock(daoFunSpec, processingEnv,
                                serverType = SERVER_TYPE_NANOHTTPD)
                        }

                    }.build()
            )
            .build())
}


/**
 * Add a function that overrides the get or post function of the NanoHTTPD
 * responder which will route the query to the appropriate function with
 * the required parameters (database, dao, gson, etc)
 */
fun TypeSpec.Builder.addNanoHttpdResponderFun(methodName: String, daoClassName: ClassName,
                                              daoTypeSpec: TypeSpec,
                                              processingEnv: ProcessingEnvironment) : TypeSpec.Builder {

    val isPostFn = methodName.toLowerCase(Locale.ROOT) == "post"
    val daoFunsToRespond =  daoTypeSpec.funSpecs.filter {
        it.httpBodyParams().isNotEmpty() == isPostFn
    }

    fun CodeBlock.Builder.addNanoHttpdReturnNotFound() : CodeBlock.Builder {
        add("%T.newFixedLengthResponse(%T.Status.NOT_FOUND, " +
                "%T.MIME_TYPE_PLAIN, %S)", NanoHTTPD::class, NanoHTTPD.Response::class,
                DoorConstants::class, "")
        return this
    }

    var daoParamNames = "_uriResource, _urlParams, _session, _dao, _gson"
    if(daoTypeSpec.daoSyncableEntitiesInSelectResults(processingEnv).isNotEmpty()) {
        daoParamNames += ",_syncHelper, _ktorHelperDao"
    }

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
                                    DoorDatabase::class.asClassName(), daoClassName))
                    .add("val _typeToken = _uriResource.initParameter(2, %T::class.java) as %T\n",
                        org.kodein.type.TypeToken::class.java,
                            org.kodein.type.TypeToken::class.parameterizedBy(DoorDatabase::class))
                    .add("val _call = %T(_uriResource, _urlParams, _session)\n", NanoHttpdCall::class)
                    .add("val _db: %T by _di.%M(_call).%M(_typeToken, tag = %T.TAG_DB)\n", DoorDatabase::class,
                            DI_ON_MEMBER, DI_INSTANCE_TYPETOKEN_MEMBER, DoorTag::class)
                    .add("val _repo: %T by _di.%M(_call).%M(_typeToken, tag = %T.TAG_REPO)\n", DoorDatabase::class,
                            DI_ON_MEMBER, DI_INSTANCE_TYPETOKEN_MEMBER, DoorTag::class)
                    .add("val _dao = _daoProvider.getDao(_db)\n")
                    .add("val _gson : %T by _di.%M()\n", Gson::class, DI_INSTANCE_MEMBER)
                    .applyIf(daoTypeSpec.daoSyncableEntitiesInSelectResults(processingEnv).isNotEmpty()) {
                        add("val _syncHelper = (_uriResource.initParameter(3, %T::class.java) as %T).getDao(_db)\n",
                            DoorDaoProvider::class, DoorDaoProvider::class.asClassName().parameterizedBy(
                            DoorDatabase::class.asClassName(), daoClassName.withSuffix("_SyncHelper")))
                        add("val _ktorHelperDao = (_uriResource.initParameter(4, %T::class.java) as %T).getDao(_db)\n",
                                    DoorDaoProvider::class, DoorDaoProvider::class.asClassName().parameterizedBy(
                                    DoorDatabase::class.asClassName(), daoClassName.withSuffix(SUFFIX_KTOR_HELPER)))
                    }
                    .apply {
                        if(daoFunsToRespond.isEmpty()) {
                            add("return ").addNanoHttpdReturnNotFound().add("\n")
                        }else {
                            beginControlFlow("return when(_fnName)")
                            daoFunsToRespond.forEach {
                                var fnParamNames = daoParamNames
                                //The repo is only required for syncable insert functions
                                if(it.isSyncableInsert(processingEnv))
                                    fnParamNames += ", _repo"

                                add("%S -> ${it.name}($fnParamNames)\n", it.name)
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

    //When there are syncable entities being inserted over the http server, we need to call the repo's
    // handleSyncEntitiesReceived to trigger event listeners
    if(daoFunSpec.isSyncableInsert(processingEnv)) {
        add("val _repo: %T = %M(_typeToken, tag = %T.TAG_REPO)\n",
            TypeVariableName.invoke("T"),
            MemberName("com.ustadmobile.door.ext", "unwrappedDbOnCall"),
            DoorTag::class)
    }

    if(daoFunSpec.isQueryWithSyncableResults(processingEnv)) {
        addKtorRouteSelectCodeBlock(daoFunSpec, processingEnv, SERVER_TYPE_KTOR)
    }else {
        addHttpServerPassToDaoCodeBlock(daoFunSpec, processingEnv, serverType = SERVER_TYPE_KTOR)
    }

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
fun CodeBlock.Builder.addKtorRouteSelectCodeBlock(daoMethod: FunSpec, processingEnv: ProcessingEnvironment,
                                     serverType: Int = DbProcessorKtorServer.SERVER_TYPE_KTOR) : CodeBlock.Builder {

    val returnTypeName = daoMethod.returnType
            ?: throw IllegalArgumentException("addKtorRouteSelectCodeBlock for ${daoMethod.name}: has null return type")

    val resultType = returnTypeName.unwrapLiveDataOrDataSourceFactory()
    val isDataSourceFactory = returnTypeName is ParameterizedTypeName
            && returnTypeName.rawType == DataSource.Factory::class.asClassName()

    val queryVarsList = daoMethod.parameters.toMutableList()
    if(isDataSourceFactory){
        queryVarsList += ParameterSpec.builder(PARAM_NAME_OFFSET, INT).build()
        queryVarsList += ParameterSpec.builder(PARAM_NAME_LIMIT, INT).build()
    }

    val syncableEntitiesList = returnTypeName.syncableEntities(processingEnv)

    if(syncableEntitiesList.isNotEmpty()) {
        addGetClientIdHeader("__clientId", serverType)

        //it is possible that clientId is already a parameter of the function (eg synchelper etc).
        // We should not add it twice.
        if(!daoMethod.parameters.any { it.name == "clientId" })
            queryVarsList  += ParameterSpec.builder("clientId", INT).build()
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
 * @param resetChangeSequenceNumbers if true and any parameter is a syncable entity then the
 * change sequence number fields will be set to zero (to ensure that the database recognizes it
 * as an updated entity). This avoids a situation where the change sequence number is not
 * incremented when using REPLACE on Sqlite (because REPLACE = DELETE then INSERT, if the primary
 * change sequence number is not zero, the trigger does not know if it was the same as before).
 * If resetChangeSequenceNumbers is null, then it will apply automatically if the DAO function
 * is receiving a syncable entity or list of syncable entities as a parameter.
 *
 * This will skip generation of getting the parameter name from the call (e.g.
 * no val __paramName = request.queryParameters["paramName"] will be generated
 */
fun CodeBlock.Builder.addHttpServerPassToDaoCodeBlock(daoMethod: FunSpec,
                                         processingEnv: ProcessingEnvironment,
                                         mutlipartHelperVarName: String? = null,
                                         beforeDaoCallCode: CodeBlock = CodeBlock.of(""),
                                         afterDaoCallCode: CodeBlock = CodeBlock.of(""),
                                         daoVarName: String = "_dao",
                                         preexistingVarNames: List<String> = listOf(),
                                         serverType: Int = DbProcessorKtorServer.SERVER_TYPE_KTOR,
                                         addRespondCall: Boolean = true,
                                         resetChangeSequenceNumbers: Boolean? = null): CodeBlock.Builder {
    val getVarsCodeBlock = CodeBlock.builder()
    val callCodeBlock = CodeBlock.builder()

    val returnType = daoMethod.returnType ?: UNIT
    if(returnType != UNIT) {
        callCodeBlock.add("val _result = ")
    }

    val isLiveData = returnType is ParameterizedTypeName
            && returnType.rawType == DoorLiveData::class.asClassName()
    val useRunBlocking = serverType == DbProcessorKtorServer.SERVER_TYPE_NANOHTTPD
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

            val isSyncableEntity = (param.type.unwrapListOrArrayComponentType() as? ClassName)
                ?.entityHasSyncableEntityTypes(processingEnv) ?: false
            val effectiveResetChangeSeqNum = resetChangeSequenceNumbers ?: isSyncableEntity
            if(effectiveResetChangeSeqNum) {
                val syncableEntityInfo = SyncableEntityInfo(param.type.asComponentClassNameIfList(),
                        processingEnv)

                getVarsCodeBlock.addRunCodeBlocksOnParamComponents(ParameterSpec("__${param.name}", param.type),
                        CodeBlock.of("${syncableEntityInfo.entityMasterCsnField.name} = 0\n"),
                        CodeBlock.of("${syncableEntityInfo.entityLocalCsnField.name} = 0\n"))
            }
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

    //If this function is an insert function for a syncable entity, this means we have incoming sync activity. We
    // need to call handleSyncEntitiesReceived on the repo so that SyncListener events are triggered
    if(daoMethod.isSyncableInsert(processingEnv)) {
        val param = daoMethod.parameters.first()
        add("(_repo·as·%T).handleSyncEntitiesReceived(%T::class, ",
            DoorDatabaseRepository::class,
            param.type.asComponentClassNameIfList())
        if(param.type.isListOrArray()) {
            add("__${param.name}")
        }else {
            add("listOf(__${param.name})")
        }
        add(")\n")
    }

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
                    MemberName("io.ktor.request", "receiveOrNull"))
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
                    takeIf { serverType == SERVER_TYPE_KTOR }?.addKtorResponse(varName,
                            addNonNullOperator = true)
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
 * Convert the given FunSpec to what is required for a KtorHelper function. This will add
 * offset and limit parameters if the DAO uses DataSource.Factory, and it will add a clientId
 * parameter.
 *
 * The return type will be the result type (e.g. DataSource.Factory or LiveData) will be unwrapped
 * @param overrides true if this is an override (e.g. abstract DAO or implementation)
 * @param annotationSpecs if not null, this replaces the original annotations for the function
 * with the given list
 */
fun FunSpec.toKtorHelperFunSpec(overrides: Boolean = false,
                                annotationSpecs: List<AnnotationSpec>? = null,
                                isAbstract: Boolean = true) : FunSpec {
    val resultType = this.returnType?.unwrapLiveDataOrDataSourceFactory()
    return this.toBuilder()
            .apply {
                //Override must be set explicitly, so remove it if it is present
                modifiers.remove(KModifier.OVERRIDE)
            }
            .applyIf(annotationSpecs != null) {
                annotations.clear()
                annotationSpecs?.also  { annotations.addAll(it) }
            }
            .applyIf(!isAbstract) {
                modifiers.remove(KModifier.ABSTRACT)
            }
            .applyIf(overrides) {
                addModifiers(KModifier.OVERRIDE)
            }
            .applyIf(this.returnType?.isDataSourceFactory() ==true) {
                addParameter(PARAM_NAME_OFFSET, INT)
                addParameter(PARAM_NAME_LIMIT, INT)
            }.applyIf(!parameters.any { it.name == "clientId" }) {
                addParameter("clientId", INT)
            }
            .apply {
                if(resultType != null)
                    returns(resultType.copy(nullable = resultType.isNullableAsSelectReturnResult))
            }
            .build()
}

/**
 * Used for generating the KTOR helper abstract DAO and implementation functions.
 * This will find the annotated query SQL and refactor it using
 * refactorSyncSelectSql.
 *
 * @param processingEnv processing environment
 * @param csnAnnotationClass MasterChangeSeqNum::class or LocalChangeSeqNum::class
 */
fun FunSpec.filterAnnotationSpecsAndRefactorToSyncableSql(processingEnv: ProcessingEnvironment,
    csnAnnotationClass: KClass<out Annotation>, addLimitAndOffset: Boolean = this.returnType?.isDataSourceFactory() == true) : List<AnnotationSpec>{
    val resultEntityClassName = returnType?.unwrapQueryResultComponentType()
            as? ClassName
            ?: throw IllegalArgumentException("${name} return type is null")

    return annotations.mapNotNull {annotationSpec ->
        if(annotationSpec.className == Query::class.asClassName()) {
            val moveLimitParam = this.parameters
                    .firstOrNull() { it.hasAnnotation(SyncableLimitParam::class.java) }?.name
            val sql = refactorSyncSelectSql(daoQuerySql(), resultEntityClassName, processingEnv,
                    csnAnnotationClass, addOffsetAndLimitParam = addLimitAndOffset,
                    moveLimitParam = moveLimitParam)
            AnnotationSpec.builder(Query::class.asClassName())
                    .addMember("%S", sql).build()
        }else {
            null
        }
    }
}

/**
 * Add a KTOR route codeblock that implements the acknwoledge (ack) entities function.
 */
fun CodeBlock.Builder.addEntitiesAckKtorRoute(trackerClassName: ClassName,
                                          entityClassName: ClassName,
                                          syncHelperVarName: String = "_syncHelper",
                                          syncHelperFnName: String = "_syncHelperFn",
                                          dbVarName: String = "_db",
                                          serverType: Int = SERVER_TYPE_KTOR) : CodeBlock.Builder {
    beginControlFlow("%M(%S)",
            DbProcessorKtorServer.POST_MEMBER, "_ack${entityClassName.simpleName}Received")
            .addRequestDi()
            .add("val _gson: %T by _di.%M()\n", Gson::class, DI_INSTANCE_MEMBER)
            .addEntitiesAckCodeBlock(trackerClassName, syncHelperVarName,
                    syncHelperFnName = syncHelperFnName, dbVarName = dbVarName, serverType = serverType)
            .endControlFlow()

    return this
}

/**
 * Add code block that implements acknowledging (ack) entities received by the client
 */
internal fun CodeBlock.Builder.addEntitiesAckCodeBlock(trackerClassName: ClassName,
                                        syncHelperVarName: String = "_syncHelper",
                                        syncHelperFnName: String = "_syncHelperFn",
                                        dbVarName: String = "_db",
                                        serverType: Int = SERVER_TYPE_KTOR): CodeBlock.Builder {

    addGetClientIdHeader("_clientId", serverType)
    addGetParamFromHttpRequest(INT, "reqId", "_requestId",
            serverType = serverType)
    add("val _ackList: %T<%T> = ", List::class.asClassName(), EntityAck::class.asClassName())
    addGetParamFromHttpRequest(
            List::class.asClassName().parameterizedBy(EntityAck::class.asClassName()),
            "ackList", serverType = serverType)
    add("\n")
    takeIf { serverType == SERVER_TYPE_KTOR }?.add("val $syncHelperVarName = $syncHelperFnName($dbVarName)\n")
    beginControlFlow("$syncHelperVarName._replace${trackerClassName.simpleName}(_ackList.map  ")
    add("%T(clientId = _clientId, csn = it.csn, epk = it.epk, rx = true)\n", trackerClassName)
    endControlFlow()
    add(")\n")
    takeIf { serverType == SERVER_TYPE_KTOR }?.add(CODEBLOCK_KTOR_NO_CONTENT_RESPOND)
    takeIf { serverType == SERVER_TYPE_NANOHTTPD }?.add(CODEBLOCK_NANOHTTPD_NO_CONTENT_RESPONSE)

    return this
}

/**
 * Adds the Ktor helper interface for the given DAO to the FileSpec
 */
fun FileSpec.Builder.addKtorHelperInterface(daoTypeSpec: TypeSpec, daoClassName: ClassName,
                                            processingEnv: ProcessingEnvironment) : FileSpec.Builder {

    addType(TypeSpec.interfaceBuilder(daoClassName.withSuffix(SUFFIX_KTOR_HELPER))
            .apply {
                daoTypeSpec.funSpecsWithSyncableSelectResults(processingEnv).forEach {
                    addFunction(it.toKtorHelperFunSpec(annotationSpecs = listOf()))
                }
            }
            .build())

    return this
}

/**
 * Adds a ktor helper abstract dao to the FileSpec. This is the local or primary variant
 * which contains all the queries that are needed by KTOR endpoints handling syncable select
 * results.
 */
fun FileSpec.Builder.addKtorAbstractDao(daoTypeSpec: TypeSpec, daoClassName: ClassName,
                                        csnAnnotationClass: KClass<out Annotation>,
                                        processingEnv: ProcessingEnvironment): FileSpec.Builder {
    val suffix = if(csnAnnotationClass == MasterChangeSeqNum::class) {
        SUFFIX_KTOR_HELPER_MASTER
    }else {
        SUFFIX_KTOR_HELPER_LOCAL
    }

    addType(TypeSpec.classBuilder(daoClassName.withSuffix(suffix))
            .addModifiers(KModifier.ABSTRACT)
            .addAnnotation(Dao::class)
            .addSuperinterface(daoClassName.withSuffix(SUFFIX_KTOR_HELPER))
            .apply {
                daoTypeSpec.funSpecsWithSyncableSelectResults(processingEnv).forEach {funSpec ->
                    addFunction(funSpec.toKtorHelperFunSpec(overrides = true,
                        annotationSpecs = funSpec.filterAnnotationSpecsAndRefactorToSyncableSql(
                                processingEnv, csnAnnotationClass)))
                }
            }
            .build()
    )

    return this
}


/**
 * Adds a Ktor Route function that will subroute all the DAOs on the given database
 */
fun FileSpec.Builder.addDbKtorRouteFunction(dbTypeEl: TypeElement,
                                            processingEnv: ProcessingEnvironment) : FileSpec.Builder {

    fun CodeBlock.Builder.addDbDaoRouteCall(daoClassName: ClassName, suffixLink: String = "_",
        addSyncHelper: Boolean, requiresKtorHelper: Boolean) : CodeBlock.Builder {
        //note syncdao suffix link = "" - and it's being generated in the sync annotation processor, not here
        val helperClasses = listOf(SUFFIX_KTOR_HELPER_MASTER, SUFFIX_KTOR_HELPER_LOCAL)
                .map {
                    daoClassName.withSuffix("$it$suffixLink$SUFFIX_JDBC_KT")
                }
        add("%M(\n_typeToken, \n", MemberName(daoClassName.packageName,
                "${daoClassName.simpleName}$SUFFIX_KTOR_ROUTE"))
        beginControlFlow("")
                .add("%T(it)\n", daoClassName.withSuffix("_$SUFFIX_JDBC_KT"))
                .endControlFlow()

        if(addSyncHelper) {
            beginControlFlow(", ")
                    .add(" %T(it)\n", dbTypeEl.asClassNameWithSuffix(SUFFIX_SYNCDAO_IMPL))
                    .endControlFlow()
        }

        if(requiresKtorHelper) {
            add(",")
            beginControlFlow("")
                    .beginControlFlow("if(_isPrimary)")
                    .add("%T(it)\n", helperClasses[0])
                    .nextControlFlow("else")
                    .add("%T(it)\n", helperClasses[1])
                    .endControlFlow()
                    .endControlFlow()
        }

        add(")\n\n")
        return this
    }



    val dbClassName = dbTypeEl.asClassName()
    addFunction(FunSpec.builder("${dbClassName.simpleName}$SUFFIX_KTOR_ROUTE")
            .receiver(Route::class)
            .addParameter(ParameterSpec.builder("_isPrimary", BOOLEAN)
                    .defaultValue(true.toString())
                    .build())
            .addCode(CodeBlock.builder()
                    .beginControlFlow("%M(%S)",
                            MemberName("io.ktor.routing", "route"),
                            dbClassName.simpleName)
                    .apply {
                        dbTypeEl.getAnnotation(MinSyncVersion::class.java)?.also {
                            add("%M(${it.value})\n",
                                    MemberName("com.ustadmobile.door.ktor",
                                            "addDbVersionCheckIntercept"))
                        }

                        add("val _typeToken: %T<%T> = %M()\n", org.kodein.type.TypeToken::class.java,
                                dbTypeEl, DbProcessorKtorServer.DI_ERASED_MEMBER)
                    }.applyIf(dbTypeEl.isDbSyncable(processingEnv)) {
                        add("%M(_typeToken)\n", MemberName("com.ustadmobile.door.ktor",
                                "UpdateNotificationsRoute"))

                        val syncDaoClassName = dbClassName.withSuffix(SUFFIX_SYNCDAO_ABSTRACT)
                        addDbDaoRouteCall(syncDaoClassName, "_", true, true)

                        dbTypeEl.allDbClassDaoGettersWithRepo(processingEnv).forEach {daoGetter ->
                            val daoTypeEl = daoGetter.returnType.asTypeElement(processingEnv)
                                    ?: throw IllegalArgumentException("${daoGetter.simpleName} has no return type?")
                            val hasKtorAndSyncHelper =daoTypeEl.isDaoThatRequiresSyncHelper(processingEnv)
                            addDbDaoRouteCall(daoTypeEl.asClassName(), "_",
                                    hasKtorAndSyncHelper, hasKtorAndSyncHelper)

                        }
                    }.applyIf(dbTypeEl.allDbEntities(processingEnv).any { it.entityHasAttachments }) {
                        add("%M(%S, _typeToken)\n", MemberName("com.ustadmobile.door.attachments",
                                "doorAttachmentsRoute"), "attachments")
                    }
                    .endControlFlow()
                    .build())
            .build())
    return this
}

/**
 * Add a NanoHTTPD mapper function for the database (maps all DAOs for this database to RouterNanoHTTPD)
 * to this FileSpec.
 */
fun FileSpec.Builder.addDbNanoHttpdMapperFunction(dbTypeElement: TypeElement,
                                          getHelpersFromDb: Boolean = true,
                                          processingEnv: ProcessingEnvironment) : FileSpec.Builder {

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
                    .applyIf(dbTypeElement.isDbSyncable(processingEnv)) {
                        val syncDaoProviderType = DoorDaoProvider::class.asTypeName().parameterizedBy(
                                dbTypeClassName, dbTypeClassName.withSuffix(SUFFIX_SYNCDAO_ABSTRACT))
                        if (getHelpersFromDb) {
                            add("val _syncDaoProvider = %T() { it._syncDao() }\n",
                                    syncDaoProviderType)
                        } else {
                            add("val _syncDaoProvider = %T(){ %T(it) }\n",
                                    syncDaoProviderType, ClassName(dbTypeClassName.packageName,
                                    "${dbTypeClassName.simpleName}${SUFFIX_SYNCDAO_IMPL}"))
                        }
                    }.apply {
                        dbTypeElement.allDbClassDaoGettersWithRepo(processingEnv).forEach { daoGetter ->
                            val daoTypeEl = daoGetter.returnType.asTypeElement(processingEnv)
                                    ?: throw IllegalArgumentException("${daoGetter.simpleName} has no return type?")
                            add("addRoute(\"\$_mappingPrefix/${daoTypeEl.simpleName}/.*\",\n " +
                                    "%T::class.java, _di,\n %T(){ it.${daoGetter.accessAsPropertyOrFunctionInvocationCall()} }, _typeToken",
                                    daoTypeEl.asClassNameWithSuffix(SUFFIX_NANOHTTPD_URIRESPONDER),
                                    DoorDaoProvider::class.asTypeName().parameterizedBy(
                                        dbTypeClassName, daoTypeEl.asClassName()))
                            if(daoTypeEl.isDaoThatRequiresSyncHelper(processingEnv)) {
                                if(getHelpersFromDb) {
                                    add("\n,")
                                    add("_syncDaoProvider,\n ")
                                    beginControlFlow(" %T()",
                                            DoorDaoProvider::class.asTypeName().parameterizedBy(
                                                    dbTypeClassName,
                                                    daoTypeEl.asClassNameWithSuffix(SUFFIX_KTOR_HELPER)))
                                    beginControlFlow("if(_isPrimary)")
                                        add("it._${daoTypeEl.simpleName}$SUFFIX_KTOR_HELPER_MASTER()\n")
                                    nextControlFlow("else")
                                        add("it._${daoTypeEl.simpleName}$SUFFIX_KTOR_HELPER_LOCAL()\n")
                                    endControlFlow()
                                    endControlFlow()
                                }
                            }
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
            FileSpec.builder(dbTypeEl.packageName, "${dbTypeEl.simpleName}$SUFFIX_KTOR_ROUTE")
                    .addDbKtorRouteFunction(dbTypeEl, processingEnv)
                    .build()
                    .writeToDirsFromArg(OPTION_KTOR_OUTPUT)
            FileSpec.builder(dbTypeEl.packageName, "${dbTypeEl.simpleName}$SUFFIX_NANOHTTPD_ADDURIMAPPING")
                    .addDbNanoHttpdMapperFunction(dbTypeEl, true, processingEnv)
                    .build()
                    .writeToDirsFromArg(OPTION_ANDROID_OUTPUT)
        }


        val daos = roundEnv.getElementsAnnotatedWith(Dao::class.java)
        daos.filter { it is TypeElement && it.isDaoWithRepository }.map { it as TypeElement }.forEach {daoTypeEl ->
            val daoBaseName = daoTypeEl.simpleName.toString()
            val daoTypeSpec = daoTypeEl.asTypeSpecStub(processingEnv)

            FileSpec.builder(daoTypeEl.packageName, "${daoTypeEl.simpleName}$SUFFIX_KTOR_ROUTE")
                    .addDaoKtorRouteFun(daoTypeSpec, daoTypeEl.asClassName(),
                            processingEnv = processingEnv)
                    .build()
                    .writeToDirsFromArg(OPTION_KTOR_OUTPUT)

            FileSpec.builder(daoTypeEl.packageName, "${daoTypeEl.simpleName}$SUFFIX_NANOHTTPD_URIRESPONDER")
                    .addNanoHttpdResponder(daoTypeSpec, daoTypeEl.asClassName(), processingEnv)
                    .build()
                    .writeToDirsFromArg(OPTION_ANDROID_OUTPUT)

            if(daoTypeEl.isDaoThatRequiresKtorHelper) {
                FileSpec.builder(daoTypeEl.packageName, "$daoBaseName$SUFFIX_KTOR_HELPER")
                        .addKtorHelperInterface(daoTypeSpec, daoTypeEl.asClassName(),
                                processingEnv)
                        .build()
                        .writeToDirsFromArg(listOf(OPTION_KTOR_OUTPUT, OPTION_ANDROID_OUTPUT))
                FileSpec.builder(daoTypeEl.packageName, "$daoBaseName$SUFFIX_KTOR_HELPER_MASTER")
                        .addKtorAbstractDao(daoTypeSpec, daoTypeEl.asClassName(),
                            MasterChangeSeqNum::class, processingEnv)
                        .build()
                        .writeToDirsFromArg(listOf(OPTION_KTOR_OUTPUT, OPTION_ANDROID_OUTPUT))
                FileSpec.builder(daoTypeEl.packageName, "$daoBaseName$SUFFIX_KTOR_HELPER_LOCAL")
                        .addKtorAbstractDao(daoTypeSpec, daoTypeEl.asClassName(),
                            LocalChangeSeqNum::class, processingEnv)
                        .build()
                        .writeToDirsFromArg(listOf(OPTION_KTOR_OUTPUT, OPTION_ANDROID_OUTPUT))
                FileSpec.builder(daoTypeEl.packageName,
                        "$daoBaseName${SUFFIX_KTOR_HELPER_MASTER}_$SUFFIX_JDBC_KT")
                        .addKtorHelperDaoImplementation(daoTypeSpec, daoTypeEl.asClassName(),
                            MasterChangeSeqNum::class, processingEnv)
                        .build()
                        .writeToDirsFromArg(OPTION_KTOR_OUTPUT)

                FileSpec.builder(daoTypeEl.packageName,
                        "$daoBaseName${SUFFIX_KTOR_HELPER_LOCAL}_$SUFFIX_JDBC_KT")
                        .addKtorHelperDaoImplementation(daoTypeSpec, daoTypeEl.asClassName(),
                                LocalChangeSeqNum::class, processingEnv)
                        .build()
                        .writeToDirsFromArg(OPTION_KTOR_OUTPUT)
            }
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

        val GET_MEMBER = MemberName("io.ktor.routing", "get")

        val POST_MEMBER = MemberName("io.ktor.routing", "post")

        val CALL_MEMBER = MemberName("io.ktor.application", "call")

        val RESPOND_MEMBER = MemberName("io.ktor.response", "respond")

        val RESPONSE_HEADER = MemberName("io.ktor.response", "header")

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