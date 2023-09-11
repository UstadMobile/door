package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.door.room.*
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import com.ustadmobile.door.*
import com.ustadmobile.door.annotation.HttpAccessible
import com.ustadmobile.door.annotation.RepoHttpBodyParam
import com.ustadmobile.door.annotation.Repository
import com.ustadmobile.door.http.RepoDaoFlowHelper
import com.ustadmobile.lib.annotationprocessor.core.AbstractDbProcessor.Companion.CLASSNAME_ILLEGALSTATEEXCEPTION
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
 * @param target The target platform
 * @param
 *
 */
fun FileSpec.Builder.addDaoRepoType(
    daoKSClass: KSClassDeclaration,
    daoClassName: ClassName,
    target: DoorTarget,
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
                addProperty(PropertySpec.builder("_repoFlowHelper", RepoDaoFlowHelper::class)
                    .initializer("%T(_repo)\n", RepoDaoFlowHelper::class)
                    .build())
            }

            daoKSClass.getAllDaoFunctionsIncSuperTypesToGenerate().forEach { daoFun ->
                //If this is OK, then remove the name param - no need for that...
                addDaoRepoFun(daoFun, daoKSClass, daoKSClass.simpleName.asString(), target, environment, resolver)
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
    daoKSFun.parameters.forEachIndexed { index, ksValueParameter ->
        if(ksValueParameter.hasAnnotation(RepoHttpBodyParam::class)) {
            add("%M(%T.Application.Json)\n",
                MemberName("io.ktor.http", "contentType"),
                ContentType::class
            )
            add("%M(%L)\n",
                MemberName("io.ktor.client.request", "setBody"),
                ksValueParameter.name?.asString()
            )
        }else {
            add("%M(%S, _repo.config.json.encodeToString(",
                MemberName("io.ktor.client.request", "parameter"),
                ksValueParameter.name?.asString())
            addKotlinxSerializationStrategy(funAsMemberOfDao.parameterTypes[index]!!, resolver)
            add(", %L))\n", ksValueParameter.name?.asString())
        }
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
) {
    add("val _response = _httpClient.")
    addRepoHttpClientRequestForFunction(daoKSFun, daoKSClass, resolver, repoValName)
    add("\n")

    add("$dbValName.%M(_response)\n",
        MemberName("com.ustadmobile.door.replication","onClientRepoDoorMessageHttpResponse"))
}

/**
 * Add a repo implementation of the given DAO FunSpec
 * @param daoKSFun the function spec for which an implementation is being generated
 * @param daoName the name of the DAO class (simple name e.g. SomeDao)
 * @param doorTarget
 */
fun TypeSpec.Builder.addDaoRepoFun(
    daoKSFun: KSFunctionDeclaration,
    daoKSClass: KSClassDeclaration,
    daoName: String,
    doorTarget: DoorTarget,
    environment: SymbolProcessorEnvironment,
    resolver: Resolver,
) : TypeSpec.Builder {
    val clientStrategy = daoKSFun.getDaoFunHttpAccessibleEffectiveStrategy(resolver)
    val daoFunSpec = daoKSFun.toFunSpecBuilder(resolver, daoKSClass.asType(emptyList()), environment.logger)
        .build()
    val functionPath = "${daoKSClass.simpleName.asString()}/${daoKSFun.simpleName.asString()}"
    val funResolved = daoKSFun.asMemberOf(daoKSClass.asType(emptyList()))

    addFunction(daoFunSpec.toBuilder()
        .removeAbstractModifier()
        .removeAnnotations()
        .addModifiers(KModifier.OVERRIDE)
        .addCode(
            CodeBlock.builder().apply {
                when(clientStrategy) {
                    HttpAccessible.ClientStrategy.PULL_REPLICATE_ENTITIES -> {
                        if(funResolved.returnType?.isFlow() == true) {
                            add("return _repoFlowHelper.asRepoFlow(\n")
                            indent()
                            add("dbFlow = _dao.").addDelegateFunctionCall(daoKSFun).add(",\n")
                            beginControlFlow("onMakeHttpRequest = ")
                            addMakeHttpRequestAndInsertReplicationsCode(daoKSFun, daoKSClass, resolver)
                            unindent().add("},")//endControlFlow
                            unindent()//unindent for asRepoFlow
                            add("\n)")
                        }else {
                            addMakeHttpRequestAndInsertReplicationsCode(daoKSFun, daoKSClass, resolver)
                            addRepoDelegateToDaoCode(daoKSFun, resolver)
                        }


                    }
                    HttpAccessible.ClientStrategy.HTTP_OR_THROW -> {
                        if(daoKSFun.hasReturnType(resolver))
                            add("return ")
                        beginControlFlow("_repo.%M(repoPath = %S)",
                            MemberName("com.ustadmobile.door.http", "repoHttpRequest"),
                           functionPath)
                        add("_repo.config.httpClient.")
                        addRepoHttpClientRequestForFunction(daoKSFun, daoKSClass, resolver)
                        add(".%M()\n", MemberName("io.ktor.client.call", "body"))
                        endControlFlow()
                    }
                    HttpAccessible.ClientStrategy.HTTP_WITH_FALLBACK -> {
                        if(daoKSFun.hasReturnType(resolver))
                            add("return ")

                        add("_repo.%M(\n",
                            MemberName("com.ustadmobile.door.http", "repoHttpRequestWithFallback"),
                        )
                        indent()
                        add("repoPath = %S,\n", functionPath)
                        beginControlFlow("http = ")
                        add("_repo.config.httpClient.")
                        addRepoHttpClientRequestForFunction(daoKSFun, daoKSClass, resolver)
                        add(".%M()\n", MemberName("io.ktor.client.call", "body"))
                        nextControlFlow(",\nfallback = ")
                        add("_dao.")
                        addDelegateFunctionCall(daoKSFun)
                        add("\n")
                        endControlFlow()
                        unindent()
                        add("\n)\n")
                    }

                    else -> {
                        addRepoDelegateToDaoCode(daoKSFun, resolver)
                    }
                }
            }
            .build()
        )
        .build())


    /*
    To be adapted shortly to handle where the repository will delegate to HTTP
    var repoMethodType = daoKSFun.getAnnotation(Repository::class)?.methodType ?: Repository.METHOD_AUTO

    if(repoMethodType == Repository.METHOD_AUTO) {
        repoMethodType = Repository.METHOD_DELEGATE_TO_DAO
    }

    //Here: in future, if needed, generate a boundary callback or something to load data in batches.

    val daoFunSpec = daoKSFun.toFunSpecBuilder(resolver, daoKSClass.asType(emptyList()), environment.logger)
        .build()
    addFunction(daoFunSpec.toBuilder()
        .removeAbstractModifier()
        .removeAnnotations()
        .addModifiers(KModifier.OVERRIDE)
        .addCode(CodeBlock.builder().apply {
            when(repoMethodType) {
                Repository.METHOD_DELEGATE_TO_DAO -> {
                    addRepoDelegateToDaoCode(daoKSFun, resolver)
                }
                Repository.METHOD_DELEGATE_TO_WEB -> {
                    //check that this is http accessible, if not, emit error
                    if(!daoKSFun.hasAnnotation(RepoHttpAccessible::class))
                        environment.logger.error("Uses delegate to web, but is not marked as http accessible",
                            daoKSFun)

                    if(doorTarget == DoorTarget.JS && daoKSFun.modifiers.contains(Modifier.SUSPEND)) {
                        add("throw %T(%S)\n", ClassName("kotlin", "IllegalStateException"),
                            "Synchronous HTTP is not supported on Door/Javascript!")
                    }else {
                        addDelegateToWebCode(daoFunSpec, daoName, doorTarget)
                    }
                }

            }
        }.build())
        .build())
    */
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
 * TODO: Update last changed by field, return primary key values from pk manager if applicable
 */
fun CodeBlock.Builder.addRepoDelegateToDaoCode(
    daoFun: KSFunctionDeclaration,
    resolver: Resolver,
) : CodeBlock.Builder{

    if(daoFun.hasReturnType(resolver))
        add("val _result = ")

    add("_dao.")
    addDelegateFunctionCall(daoFun)
    add("\n")

    if(daoFun.hasReturnType(resolver)) {
        add("return _result\n")
    }

    return this
}

fun CodeBlock.Builder.addDelegateToWebCode(
    daoFunSpec: FunSpec,
    daoName: String,
    target: DoorTarget
) : CodeBlock.Builder {
    if(target == DoorTarget.JS) {
        add("throw %T(%S)\n", CLASSNAME_ILLEGALSTATEEXCEPTION,
            "${daoName}.${daoFunSpec.name} : non-suspended delegate to web not supported on JS")
        return this
    }

    if(daoFunSpec.hasReturnType) {
        add("return ")
    }

    if(!daoFunSpec.isSuspended) {
        beginRunBlockingControlFlow()
    }

    addKtorRequestForFunction(daoFunSpec, daoName = daoName,
        addClientIdHeaderVar = "_clientId")

    if(!daoFunSpec.isSuspended) {
        endControlFlow()
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
                    daoKSClass.toClassName(), target, resolver = resolver, environment = environment)
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