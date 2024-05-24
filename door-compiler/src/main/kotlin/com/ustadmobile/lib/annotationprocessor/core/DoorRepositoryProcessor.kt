package com.ustadmobile.lib.annotationprocessor.core

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.RepositoryHelper
import com.ustadmobile.door.annotation.HttpAccessible
import com.ustadmobile.door.annotation.RepoHttpBodyParam
import com.ustadmobile.door.annotation.Repository
import com.ustadmobile.door.http.RepoDaoFlowHelper
import com.ustadmobile.door.http.RepositoryDaoWithFlowHelper
import com.ustadmobile.door.paging.DoorRepositoryHttpRequestPagingSource
import com.ustadmobile.door.paging.DoorRepositoryReplicatePullPagingSource
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.lib.annotationprocessor.core.DoorRepositoryProcessor.Companion.SUFFIX_REPOSITORY2
import com.ustadmobile.lib.annotationprocessor.core.ext.*
import io.ktor.client.*
import io.ktor.http.*

/**
 * Add a TypeSpec to the given FileSpec Builder that is an implementation of the repository for a
 * database as per the dbKSClassDeclaration parameter.
 */
fun FileSpec.Builder.addDbRepoType(
    dbKSClassDeclaration: KSClassDeclaration,
    target: DoorTarget
): FileSpec.Builder {
    addType(TypeSpec.classBuilder(dbKSClassDeclaration.toClassNameWithSuffix(SUFFIX_REPOSITORY2))
        .addOriginatingKSClass(dbKSClassDeclaration)
        .superclass(dbKSClassDeclaration.toClassName())
        .addSuperinterface(DoorDatabaseRepository::class)
        .addAnnotation(AnnotationSpec.builder(Suppress::class)
            .addMember("%S, %S, %S, %S", "LocalVariableName", "PropertyName", "FunctionName",
                "ClassName")
            .build())
        .primaryConstructor(FunSpec.constructorBuilder()
            .addParameter("db", dbKSClassDeclaration.toClassName())
            .addParameter("dbUnwrapped", dbKSClassDeclaration.toClassName())
            .addParameter("config", RepositoryConfig::class)
            .build())
        .addProperty(PropertySpec.builder("config", RepositoryConfig::class)
            .addModifiers(KModifier.OVERRIDE)
            .initializer("config")
            .build())
        .addProperty(PropertySpec.builder("context", Any::class)
            .getter(FunSpec.getterBuilder()
                .addCode("return config.context\n")
                .build())
            .build())
        .addProperty(PropertySpec.builder("_db", dbKSClassDeclaration.toClassName())
            .addModifiers(KModifier.PRIVATE)
            .initializer("dbUnwrapped")
            .build())
        .addProperty(PropertySpec.builder("db",
                dbKSClassDeclaration.toClassName()).initializer("db")
            .addModifiers(KModifier.OVERRIDE)
            .build())
        .addProperty(PropertySpec.builder("_endpoint",
            String::class.asClassName())
            .addModifiers(KModifier.PRIVATE)
            .getter(FunSpec.getterBuilder()
                .addCode("return config.endpoint")
                .build())
            .build())
        .addProperty(PropertySpec.builder("_httpClient",
            HttpClient::class.asClassName())
            .getter(FunSpec.getterBuilder()
                .addCode("return config.httpClient\n")
                .build())
            .build())
        .addProperty(PropertySpec.builder("_repositoryHelper", RepositoryHelper::class)
            .initializer("%T(db, config)\n", RepositoryHelper::class)
            .build())
        .addProperty(PropertySpec.builder("clientId", LONG)
            .getter(FunSpec.getterBuilder().addCode("return config.nodeId\n").build())
            .build())
        .addProperty(PropertySpec.builder("dbName", String::class, KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return \"Repository for [\${_db.toString()}] - \${config.endpoint}\"\n")
                .build())
            .build())
        .addFunction(FunSpec.builder("clearAllTables")
            .addModifiers(KModifier.OVERRIDE)
            .addCode("throw %T(%S)\n", ClassName("kotlin", "IllegalStateException"),
                "Cannot use a repository to clearAllTables!")
            .build())
        .applyIf(target == DoorTarget.JS) {
            addThrowExceptionOverride("clearAllTablesAsync", suspended = true)
        }
        .addRepositoryHelperDelegateCalls("_repositoryHelper")
        .applyIf(target != DoorTarget.ANDROID) {
            addFunction(FunSpec.builder("createAllTables")
                .returns(List::class.parameterizedBy(String::class))
                .addModifiers(KModifier.OVERRIDE)
                .addCode("throw %T(%S)\n",
                    ClassName("kotlin", "IllegalStateException"),
                    "Cannot use a repository to createAllTables!")
                .build())
            addOverrideInvalidationTracker("_db")
            addDbVersionProperty(dbKSClassDeclaration)
        }
        .applyIf(target == DoorTarget.ANDROID) {
            addRoomCreateInvalidationTrackerFunction()
            addOverrideInvalidationTracker("_db")
            addRoomDatabaseCreateOpenHelperFunction()
        }
        .apply {
            dbKSClassDeclaration.allDbClassDaoGetters().forEach { daoGetterOrProp ->
                addRepoDbDaoAccessor(daoGetterOrProp)
            }
        }
        .addType(TypeSpec.companionObjectBuilder()
            .addProperty(PropertySpec.builder(DoorRepositoryProcessor.DB_NAME_VAR, String::class)
                .addModifiers(KModifier.CONST)
                .initializer("%S", dbKSClassDeclaration.simpleName.asString())
                .mutable(false).build())
            .build())

        .build())
    return this
}


private fun TypeSpec.Builder.addRepoDbDaoAccessor(
    daoGetterOrProp: KSDeclaration
): TypeSpec.Builder {
    val daoKSDecl = daoGetterOrProp.propertyOrReturnType()?.resolve()?.declaration as? KSClassDeclaration
        ?: throw IllegalArgumentException("addRepoDbDaoAccessor: no return type ")
    if(!daoKSDecl.hasAnnotation(Repository::class)) {
        addDaoPropOrGetterOverride(daoGetterOrProp, CodeBlock.of("throw %T(%S)\n",
                ClassName("kotlin", "IllegalStateException"),
                "${daoKSDecl.simpleName.asString()} is not annotated with @Repository"))
        return this
    }

    addProperty(PropertySpec.builder("_${daoKSDecl.simpleName.asString()}",
            daoKSDecl.toClassNameWithSuffix(SUFFIX_REPOSITORY2),
            KModifier.PRIVATE)
        .delegate(CodeBlock.builder().beginControlFlow("lazy")
            .add("%T(db, this, db.%L, _httpClient, clientId, _endpoint)\n",
                daoKSDecl.toClassNameWithSuffix(SUFFIX_REPOSITORY2),
                daoGetterOrProp.toPropertyOrEmptyFunctionCaller())
            .endControlFlow()
            .build())
        .build())

    addDaoPropOrGetterOverride(daoGetterOrProp, CodeBlock.of("return  _${daoKSDecl.simpleName.asString()}"))

    return this
}

/**
 * Add a TypeSpec repository implementation for the given DAO as given by daoTypeSpec
 *
 * @param daoKSClass The KSClassDeclaration containing the FunSpecs for this DAO
 * @param daoClassName Classname for the abstract DAO class
 * @param
 *
 */
fun FileSpec.Builder.addDaoRepoType(
    daoKSClass: KSClassDeclaration,
    daoClassName: ClassName,
    extraConstructorParams: List<ParameterSpec> = listOf(),
    resolver: Resolver,
    environment: SymbolProcessorEnvironment,
): FileSpec.Builder {

    addType(TypeSpec.classBuilder("${daoKSClass.simpleName.asString()}$SUFFIX_REPOSITORY2")
        .addOriginatingKSClass(daoKSClass)
        .addProperty(PropertySpec.builder("_db", RoomDatabase::class)
            .initializer("_db").build())
        .addProperty(PropertySpec.builder("_repo", DoorDatabaseRepository::class)
            .initializer("_repo").build())
        .addProperty(PropertySpec.builder("_dao",
            daoClassName).initializer("_dao").build())
        .addProperty(PropertySpec.builder("_httpClient",
            HttpClient::class).initializer("_httpClient").build())
        .addProperty(PropertySpec.builder("_clientId", Long::class)
            .initializer("_clientId").build())
        .addProperty(PropertySpec.builder("_endpoint", String::class)
            .initializer("_endpoint").build())
        .addSuperClassOrInterface(daoKSClass)
        .addAnnotation(AnnotationSpec.builder(Suppress::class)
            .addMember("%S, %S, %S", "REDUNDANT_PROJECTION", "LocalVariableName",
                "ClassName")
            .build())
        .primaryConstructor(FunSpec.constructorBuilder()
            .addParameter("_db", RoomDatabase::class)
            .addParameter("_repo", DoorDatabaseRepository::class)
            .addParameter("_dao", daoClassName)
            .addParameter("_httpClient", HttpClient::class)
            .addParameter("_clientId", Long::class)
            .addParameter("_endpoint", String::class)
            .apply {
                takeIf { extraConstructorParams.isNotEmpty() }?.addParameters(extraConstructorParams)
            }
            .build())
        .apply {
            val needsRepoFlowHelper = daoKSClass.getAllDaoFunctionsIncSuperTypesToGenerate().any {
                it.getDaoFunHttpAccessibleEffectiveStrategy(resolver) == HttpAccessible.ClientStrategy.PULL_REPLICATE_ENTITIES
                        && it.asMemberOf(daoKSClass.asType(emptyList())).returnType?.isFlow() == true
            }
            if(needsRepoFlowHelper) {
                addSuperinterface(RepositoryDaoWithFlowHelper::class)
                addProperty(PropertySpec.builder("repoDaoFlowHelper", RepoDaoFlowHelper::class)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("%T(_repo)\n", RepoDaoFlowHelper::class)
                    .build())
            }

            daoKSClass.getAllDaoFunctionsIncSuperTypesToGenerate().forEach { daoFun ->
                //If this is OK, then remove the name param - no need for that...
                addDaoRepoFun(daoFun, daoKSClass, environment, resolver)
            }
        }
        .build())

    return this
}

/**
 * Add a block of code to make an http request for a given function within a repo: e.g.
 *
 * .get {
 *     setRepoUrl(config, "DaoName/functionName")
 *     doorNodeIdHeader(repo)
 *     param("paramName", _repo.config.json.encodeToString(Type.serializer(), paramName)
 * }
 */
fun CodeBlock.Builder.addRepoHttpClientRequestForFunction(
    daoKSFun: KSFunctionDeclaration,
    daoKSClass: KSClassDeclaration,
    resolver: Resolver,
    repoValName: String = "_repo",
    pagingParamsValName: String? = null,
) : CodeBlock.Builder {
    val httpMethod = daoKSFun.getDaoFunHttpMethodToUse()
    val funAsMemberOfDao = daoKSFun.asMemberOf(daoKSClass.asType(emptyList()))
    beginControlFlow("%M",
        MemberName("io.ktor.client.request", httpMethod.lowercase()))
    add("%M($repoValName.config, %S)\n",
        MemberName("com.ustadmobile.door.ext", "setRepoUrl"),
        "${daoKSClass.simpleName.asString()}/${daoKSFun.simpleName.asString()}")
    add("%M($repoValName)\n",
        MemberName("com.ustadmobile.door.ext", "doorNodeIdHeader"))
    add("%M(%S, %S)\n",
        MemberName("io.ktor.client.request", "header"),
        "cache-control",
        "no-store")

    daoKSFun.parameters.forEachIndexed { index, ksValueParameter ->
        if(ksValueParameter.hasAnnotation(RepoHttpBodyParam::class)) {
            add("%M(%T.Application.Json)\n",
                MemberName("io.ktor.http", "contentType"),
                ContentType::class
            )

            add("%M(\n",
                MemberName("com.ustadmobile.door.ext", "setBodyJson"),
            )
            withIndent {
                add("json = _repo.config.json,\n")
                add("serializer = ")
                addKotlinxSerializationStrategy(funAsMemberOfDao.parameterTypes[index]!!, resolver).add(",\n")
                add("value = %L,\n", ksValueParameter.name?.asString())
            }
            add(")\n")
        }else {
            add("%M(%S, _repo.config.json.encodeToString(",
                MemberName("io.ktor.client.request", "parameter"),
                ksValueParameter.name?.asString())
            addKotlinxSerializationStrategy(funAsMemberOfDao.parameterTypes[index]!!, resolver)
            add(", %L))\n", ksValueParameter.name?.asString())
        }
    }

    if(pagingParamsValName != null) {
        add("%M(\n", MemberName("com.ustadmobile.door.ext", "pagingSourceLoadParameters"))
        indent()
        add("json = _repo.config.json, \n")
        add("keySerializer = ")
        addKotlinxSerializationStrategy(resolver.builtIns.intType.makeNullable(), resolver).add(",\n")
        add("loadParams = $pagingParamsValName\n")
        unindent()
        add(")\n")
    }

    endControlFlow()

    return this
}

/**
 * For a given DAO function where the ClientStrategy is to use replicate entities - generate code that will make the
 * http request and pass the received entities to the NodeEventManager (which is responsible
 * to insert them via receive view, directly, or triggering the callback).
 */
fun CodeBlock.Builder.addMakeHttpRequestAndInsertReplicationsCode(
    daoKSFun: KSFunctionDeclaration,
    daoKSClass: KSClassDeclaration,
    resolver: Resolver,
    repoValName: String = "_repo",
    dbValName: String = "_db",
    pagingParamsValName: String? = null,
) {
    //When using a paging source, we need to return end of pagination reached
    val fnName = if(pagingParamsValName != null) {
        "replicateHttpRequestOrThrow"
    }else {
        "replicateHttpRequestCatchAndLog"
    }

    beginControlFlow("_repo.%M(repoPath = %S)",
        MemberName("com.ustadmobile.door.http", fnName),
        "${daoKSClass.simpleName.asString()}/${daoKSFun.simpleName.asString()}"
    )
    add("val _response = _httpClient.")
    addRepoHttpClientRequestForFunction(
        daoKSFun = daoKSFun,
        daoKSClass = daoKSClass,
        resolver = resolver,
        repoValName = repoValName,
        pagingParamsValName = pagingParamsValName
    )
    add("\n")

    add("$dbValName.%M(_response, _repo.config.json)\n",
        MemberName("com.ustadmobile.door.replication","onClientRepoDoorMessageHttpResponse"))

    if(pagingParamsValName != null) {
        add("_response.%M()\n", MemberName("com.ustadmobile.door.paging", "endOfPaginationReached"))
    }

    endControlFlow()
}

/**
 * Add a repo implementation of the given DAO FunSpec
 * @param daoKSFun the function spec for which an implementation is being generated
 */
fun TypeSpec.Builder.addDaoRepoFun(
    daoKSFun: KSFunctionDeclaration,
    daoKSClass: KSClassDeclaration,
    environment: SymbolProcessorEnvironment,
    resolver: Resolver,
) : TypeSpec.Builder {
    val clientStrategy = daoKSFun.getDaoFunHttpAccessibleEffectiveStrategy(resolver)
    val daoFunSpec = daoKSFun.toFunSpecBuilder(resolver, daoKSClass.asType(emptyList()), environment.logger)
        .build()
    val functionPath = "${daoKSClass.simpleName.asString()}/${daoKSFun.simpleName.asString()}"
    val funResolved = daoKSFun.asMemberOf(daoKSClass.asType(emptyList()))


    fun CodeBlock.Builder.addHttpPagingSource(withFallback: Boolean) {
        add("%T(\n", DoorRepositoryHttpRequestPagingSource::class)
        val componentType = funResolved.returnType?.unwrapResultType(resolver)
            ?: throw IllegalArgumentException("addDaoRepoFun ${daoKSFun.simpleName.asString()} " +
                    " cannot resolve result type")
        withIndent {
            add("valueDeserializationStrategy = ")
                .addKotlinxSerializationStrategy(componentType, resolver)
                .add(",\n")
            add("json = _repo.config.json,\n")
            beginControlFlow("onLoadHttp = ")
            add("_pagingLoadParams ->\n")
            add("_repo.config.httpClient.")
            addRepoHttpClientRequestForFunction(daoKSFun, daoKSClass, resolver,
                pagingParamsValName = "_pagingLoadParams")
            endControlFlow()
            add(",")
            if(withFallback) {
                add("fallbackPagingSource = _dao.").addDelegateFunctionCall(daoKSFun).add("\n")
            }
        }
        add(")\n")
    }


    addFunction(daoFunSpec.toBuilder()
        .removeAbstractModifier()
        .removeAnnotations()
        .addModifiers(KModifier.OVERRIDE)
        .addCode(
            CodeBlock.builder().apply {
                when(clientStrategy) {
                    HttpAccessible.ClientStrategy.PULL_REPLICATE_ENTITIES -> {
                        if(funResolved.returnType?.isFlow() == true) {
                            add("return repoDaoFlowHelper.asRepoFlow(\n")
                            indent()
                            add("dbFlow = _dao.").addDelegateFunctionCall(daoKSFun).add(",\n")
                            beginControlFlow("onMakeHttpRequest = ")
                            addMakeHttpRequestAndInsertReplicationsCode(daoKSFun, daoKSClass, resolver)
                            unindent().add("},")//endControlFlow
                            unindent()//unindent for asRepoFlow
                            add("\n)")
                        }else if(funResolved.returnType?.isPagingSource() == true) {
                            add("return %T(", DoorRepositoryReplicatePullPagingSource::class)
                            indent()
                            add("\nrepo = _repo,\n")
                            add("repoPath = %S,\n", functionPath)
                            add("dbPagingSource = _dao.").addDelegateFunctionCall(daoKSFun).add(",\n")
                            beginControlFlow("onLoadHttp = ")
                            add("_pagingParams -> \n")
                            addMakeHttpRequestAndInsertReplicationsCode(daoKSFun, daoKSClass, resolver,
                                pagingParamsValName = "_pagingParams")
                            unindent().add("},")//endControlFlow
                            unindent()//unindent for asRepoFlow
                            add("\n)")
                        } else {
                            addMakeHttpRequestAndInsertReplicationsCode(daoKSFun, daoKSClass, resolver)
                            addRepoDelegateToDaoCode(daoKSFun, daoKSClass, resolver, environment)
                        }


                    }
                    HttpAccessible.ClientStrategy.HTTP_OR_THROW -> {
                        if(daoKSFun.hasReturnType(resolver))
                            add("return ")

                        if(funResolved.returnType?.isPagingSource() == true) {
                            addHttpPagingSource(withFallback = false)
                        }else {
                            add("_repo.config.json.decodeFromString(\n")
                            withIndent {
                                add("deserializer = ")
                                addKotlinxSerializationStrategy(funResolved.returnType ?: resolver.builtIns.unitType,
                                    resolver)
                                add(",\n")
                                add("string =")
                                beginControlFlow("_repo.%M(repoPath = %S)",
                                    MemberName("com.ustadmobile.door.http", "repoHttpRequest"),
                                    functionPath)
                                add("_repo.config.httpClient.")
                                addRepoHttpClientRequestForFunction(daoKSFun, daoKSClass, resolver)
                                add(".%M()\n", MemberName("io.ktor.client.statement", "bodyAsText"))
                                endControlFlow()
                            }
                            add(")\n")
                        }

                    }
                    HttpAccessible.ClientStrategy.HTTP_WITH_FALLBACK -> {
                        if(daoKSFun.hasReturnType(resolver))
                            add("return ")

                        if(funResolved.returnType?.isPagingSource() == true) {
                            addHttpPagingSource(withFallback = true)
                        }else{
                            add("_repo.%M(\n",
                                MemberName("com.ustadmobile.door.http", "repoHttpRequestWithFallback"),
                            )
                            indent()
                            add("repoPath = %S,\n", functionPath)
                            beginControlFlow("http = ")
                            add("_repo.config.json.decodeFromString(\n")
                            withIndent {
                                add("deserializer = ")
                                addKotlinxSerializationStrategy(
                                    funResolved.returnType ?: resolver.builtIns.unitType, resolver)
                                add(",\n")
                                add("string = ")
                                add("_repo.config.httpClient.")
                                addRepoHttpClientRequestForFunction(daoKSFun, daoKSClass, resolver)
                                add(".%M()\n", MemberName("io.ktor.client.statement", "bodyAsText"))
                            }
                            add(")\n")

                            nextControlFlow(",\nfallback = ")
                            add("_dao.")
                            addDelegateFunctionCall(daoKSFun)
                            add("\n")
                            endControlFlow()
                            unindent()
                            add("\n)\n")
                        }
                    }

                    else -> {
                        addRepoDelegateToDaoCode(daoKSFun, daoKSClass, resolver, environment)
                    }
                }
            }
            .build()
        )
        .build())

    return this
}

/**
 * Write a simple function call - assuming that all the same parameters are present
 */
fun CodeBlock.Builder.addDelegateFunctionCall(
    daoFun: KSFunctionDeclaration,
) : CodeBlock.Builder{
    add("${daoFun.simpleName.asString()}(")
    add(daoFun.parameters.joinToString { it.name?.asString() ?: "" })
    add(")")
    return this
}

/**
 * Add a CodeBlock for a repo delegate to DAO function. This will
 *
 * 1) Set the primary key on any entities that don't have a primary key set
 * 2) Update the change sequence numbers when running an update
 * 3) Pass the work to the DAO and return the result
 *
 */
fun CodeBlock.Builder.addRepoDelegateToDaoCode(
    daoFun: KSFunctionDeclaration,
    daoKSClass: KSClassDeclaration,
    resolver: Resolver,
    environment: SymbolProcessorEnvironment,
) : CodeBlock.Builder{
    val modifiedTableName = daoFun.getDaoFunctionModifiedTableName(daoKSClass, resolver, environment)

    if(daoFun.hasReturnType(resolver))
        add("val _result = ")

    if(modifiedTableName != null) {
        beginControlFlow("_repo.%M(%S)",
            MemberName(
                packageName = "com.ustadmobile.door.replication",
                simpleName = if(daoFun.isSuspended) "withRepoChangeMonitorAsync" else "withRepoChangeMonitor"
            ),
            modifiedTableName)
    }
    add("_dao.")
    addDelegateFunctionCall(daoFun)
    add("\n")
    if(modifiedTableName != null) {
        endControlFlow()
    }

    if(daoFun.hasReturnType(resolver)) {
        add("return _result\n")
    }

    return this
}

class DoorRepositoryProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val dbSymbols = resolver.getDatabaseSymbolsToProcess()
            .filter { dbKSClassDecl ->
                dbKSClassDecl.dbEnclosedDaos().any { it.hasAnnotation(Repository::class) }
            }

        val daoSymbols = resolver.getDaoSymbolsToProcess().filter { it.hasAnnotation(Repository::class) }

        val target = environment.doorTarget(resolver)

        dbSymbols.forEach { dbKSClass ->
            FileSpec.builder(dbKSClass.packageName.asString(),
                "${dbKSClass.simpleName.asString()}$SUFFIX_REPOSITORY2")
                .addDbRepoType(dbKSClass, target)
                .build()
                .writeTo(environment.codeGenerator, false)
        }

        daoSymbols.forEach { daoKSClass ->
            FileSpec.builder(daoKSClass.packageName.asString(),
                "${daoKSClass.simpleName.asString()}$SUFFIX_REPOSITORY2")
                .addDaoRepoType(daoKSClass,
                    daoKSClass.toClassName(), resolver = resolver, environment = environment)
                .build()
                .writeTo(environment.codeGenerator, false)
        }

        return emptyList()
    }

    companion object {
        //including the underscore as it should
        const val SUFFIX_REPOSITORY2 = "_Repo"

        /**
         * A static string which is generated for the database name part of the http path, which is
         * passed from the database repository to the DAO repository so it can use the correct http
         * path e.g. endpoint/dbname/daoname
         */
        const val DB_NAME_VAR = "_DB_NAME"

    }
}