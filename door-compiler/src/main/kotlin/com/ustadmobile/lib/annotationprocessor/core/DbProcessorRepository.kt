package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.*
import com.ustadmobile.door.annotation.RepoHttpAccessible
import com.ustadmobile.door.annotation.Repository
import com.ustadmobile.door.attachments.EntityWithAttachment
import com.ustadmobile.door.replication.ReplicationSubscriptionManager
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_ANDROID_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_JS_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_JVM_DIRS
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorRepository.Companion.BOUNDARY_CALLBACK_CLASSNAME
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorRepository.Companion.DATASOURCEFACTORY_TO_BOUNDARYCALLBACK_VARNAME
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorRepository.Companion.SUFFIX_ENTITY_WITH_ATTACHMENTS_ADAPTER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorRepository.Companion.SUFFIX_REPOSITORY2
import io.ktor.client.*
import java.util.*
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

/**
 * Generate the table id map of entity names (strings) to the table id as per the syncableentity
 * annotation
 */
fun TypeSpec.Builder.addTableIdMapProperty(dbTypeElement: TypeElement, processingEnv: ProcessingEnvironment) : TypeSpec.Builder {
    addProperty(PropertySpec.builder("TABLE_ID_MAP",
            Map::class.asClassName().parameterizedBy(String::class.asClassName(), INT))
            .initializer(CodeBlock.builder()
                    .add("mapOf<String, Int>(")
                    .add(")\n")
                    .build())
            .build())

    return this
}

/**
 * Add a TypeSpec to the given FileSpec Builder that is an implementation of the repository for a
 * database as per the dbTypeElement parameter.
 */
fun FileSpec.Builder.addDbRepoType(
    dbTypeElement: TypeElement,
    processingEnv: ProcessingEnvironment,
    syncDaoMode: Int = DbProcessorRepository.REPO_SYNCABLE_DAO_CONSTRUCT,
    overrideClearAllTables: Boolean = true,
    overrideSyncDao: Boolean = false,
    overrideOpenHelper: Boolean = false,
    addDbVersionProp: Boolean = false,
    overrideKtorHelpers: Boolean = false,
    overrideDataSourceProp: Boolean = false,
    overrideWrapDbForTransaction: Boolean = false,
    target: DoorTarget
): FileSpec.Builder {
    addType(TypeSpec.classBuilder(dbTypeElement.asClassNameWithSuffix(SUFFIX_REPOSITORY2))
            .superclass(dbTypeElement.asClassName())
            .addSuperinterface(DoorDatabaseRepository::class)
            .addAnnotation(AnnotationSpec.builder(Suppress::class)
                    .addMember("%S, %S, %S, %S", "LocalVariableName", "PropertyName", "FunctionName",
                            "ClassName")
                    .build())
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("db", dbTypeElement.asClassName())
                .addParameter("dbUnwrapped", dbTypeElement.asClassName())
                .addParameter("config", RepositoryConfig::class)
                .addParameter(ParameterSpec.builder("isRootRepository", BOOLEAN)
                    .defaultValue("false")
                    .build())
                .build())
            .addProperty(PropertySpec.builder("config", RepositoryConfig::class)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("config")
                    .build())
            .addProperty(PropertySpec.builder("isRootRepository", BOOLEAN)
                .addModifiers(KModifier.OVERRIDE)
                .initializer("isRootRepository")
                .build())
            .addProperty(PropertySpec.builder("context", Any::class)
                    .getter(FunSpec.getterBuilder()
                            .addCode("return config.context\n")
                            .build())
                    .build())
            .addProperty(PropertySpec.builder("_db", dbTypeElement.asClassName())
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("dbUnwrapped")
                    .build())
            .addProperty(PropertySpec.builder("replicationSubscriptionManager",
                ReplicationSubscriptionManager::class.asTypeName().copy(nullable = true))
                .addModifiers(KModifier.OVERRIDE)
                .initializer(CodeBlock.builder()
                    .beginControlFlow("if(isRootRepository && config.useReplicationSubscription)")
                    .add("%M()\n", MemberName("com.ustadmobile.door.replication",
                        "makeNewSubscriptionManager"))
                    .nextControlFlow("else")
                    .add("null\n")
                    .endControlFlow()
                    .build())
                .build())
            .addProperty(PropertySpec.builder("db",
                    dbTypeElement.asClassName()).initializer("db")
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
                    .initializer("%T()", RepositoryHelper::class)
                    .build())
            .addProperty(PropertySpec.builder("tableIdMap",
                    Map::class.asClassName().parameterizedBy(String::class.asClassName(), INT))
                    .getter(FunSpec.getterBuilder().addCode("return TABLE_ID_MAP\n").build())
                    .addModifiers(KModifier.OVERRIDE)
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
            .applyIf(overrideClearAllTables) {
                addFunction(FunSpec.builder("createAllTables")
                        .addModifiers(KModifier.OVERRIDE)
                        .addCode("throw %T(%S)\n",
                                ClassName("kotlin", "IllegalStateException"),
                                "Cannot use a repository to createAllTables!")
                        .build())
            }
            .applyIf(overrideOpenHelper) {
                addRoomCreateInvalidationTrackerFunction()
                addOverrideGetRoomInvalidationTracker("_db")
                addRoomDatabaseCreateOpenHelperFunction()
            }
            .applyIf(addDbVersionProp) {
                addDbVersionProperty(dbTypeElement)
            }
            .applyIf(overrideWrapDbForTransaction) {
                addFunction(FunSpec.builder("wrapForNewTransaction")
                    .addOverrideWrapNewTransactionFun()
                    .addCode("return transactionDb.%M(this as T, dbKClass as KClass<T>, this::class as KClass<T>) as T\n",
                        MemberName("com.ustadmobile.door.ext", "wrapDbAsRepositoryForTransaction"),
                        )
                    .build())
            }
            .apply {
                dbTypeElement.allDbClassDaoGetters(processingEnv).forEach { daoGetter ->
                    addRepoDbDaoAccessor(daoGetter, processingEnv)
                }
            }
            .addType(TypeSpec.companionObjectBuilder()
                    .addTableIdMapProperty(dbTypeElement, processingEnv)
                    .addProperty(PropertySpec.builder(DbProcessorRepository.DB_NAME_VAR, String::class)
                            .addModifiers(KModifier.CONST)
                            .initializer("%S", dbTypeElement.simpleName)
                            .mutable(false).build())
                    .build())
            .build())

    return this
}

/**
 * Generate an EntityWithAttachment inline adapter class
 */
fun FileSpec.Builder.addEntityWithAttachmentAdapterType(entityWithAttachment: TypeElement,
    processingEnv: ProcessingEnvironment) : FileSpec.Builder {
    val attachmentInfo = EntityAttachmentInfo(entityWithAttachment)
    val nullableStringClassName = String::class.asClassName().copy(nullable = true)
    addType(TypeSpec.classBuilder("${entityWithAttachment.simpleName}${SUFFIX_ENTITY_WITH_ATTACHMENTS_ADAPTER}")
            .addModifiers(KModifier.INLINE)
            .addSuperinterface(EntityWithAttachment::class)
            .primaryConstructor(
                    FunSpec.constructorBuilder()
                            .addParameter("entity", entityWithAttachment.asClassName())
                            .build())
            .addProperty(
                    PropertySpec.builder("entity", entityWithAttachment.asClassName())
                            .addModifiers(KModifier.PRIVATE)
                            .initializer("entity")
                            .build())
            .addProperty(
                    PropertySpec.builder("attachmentUri", nullableStringClassName, KModifier.OVERRIDE)
                            .mutable(mutable = true)
                            .delegateGetterAndSetter("entity.${attachmentInfo.uriPropertyName}")
                            .build())
            .addProperty(
                    PropertySpec.builder("attachmentMd5", nullableStringClassName, KModifier.OVERRIDE)
                            .mutable(true)
                            .delegateGetterAndSetter("entity.${attachmentInfo.md5PropertyName}")
                            .build())
            .addProperty(
                    PropertySpec.builder("attachmentSize", INT, KModifier.OVERRIDE)
                            .mutable(true)
                            .delegateGetterAndSetter("entity.${attachmentInfo.sizePropertyName}")
                            .build())
            .addProperty(
                    PropertySpec.builder("tableName", String::class, KModifier.OVERRIDE)
                            .getter(FunSpec.getterBuilder()
                                    .addCode("return %S\n", entityWithAttachment.simpleName)
                                    .build())
                            .build()
            )
            .build())

    return this
}

/**
 * Generate an extension function that will return the entitywithadapter
 * e.g. EntityName.asEntityWithAttachment()
 */
fun FileSpec.Builder.addAsEntityWithAttachmentAdapterExtensionFun(entityWithAttachment: TypeElement): FileSpec.Builder {
    addFunction(FunSpec.builder("asEntityWithAttachment")
            .addModifiers(KModifier.INLINE)
            .receiver(entityWithAttachment.asClassName())
            .returns(EntityWithAttachment::class)
            .addCode("return %T(this)\n",
                    entityWithAttachment.asClassNameWithSuffix(SUFFIX_ENTITY_WITH_ATTACHMENTS_ADAPTER))
            .build())
    return this
}


/**
 * Add an accessor function for the given dao accessor (and any related ktor helper daos if
 * specified).
 */
private fun TypeSpec.Builder.addRepoDbDaoAccessor(
    daoGetter: ExecutableElement,
    processingEnv: ProcessingEnvironment
) : TypeSpec.Builder{
    val daoTypeEl = daoGetter.returnType.asTypeElement(processingEnv)
            ?: throw IllegalArgumentException("Dao getter has no return type")
    if(!daoTypeEl.hasAnnotation(Repository::class.java)) {
        addAccessorOverride(daoGetter, CodeBlock.of("throw %T(%S)\n",
                ClassName("kotlin", "IllegalStateException"),
                "${daoTypeEl.simpleName} is not annotated with @Repository"))
        return this
    }

    addProperty(PropertySpec.builder("_${daoTypeEl.simpleName}",
                daoTypeEl.asClassNameWithSuffix(SUFFIX_REPOSITORY2))
            .delegate(CodeBlock.builder().beginControlFlow("lazy")
                    .add("%T(db, this, db.%L, _httpClient, clientId, _endpoint)",
                            daoTypeEl.asClassNameWithSuffix(SUFFIX_REPOSITORY2),
                            daoGetter.makeAccessorCodeBlock())
                    .endControlFlow()
                    .build())
            .build())

    addAccessorOverride(daoGetter, CodeBlock.of("return  _${daoTypeEl.simpleName}"))

    return this
}

/**
 * Add a TypeSpec repository implementation for the given DAO as given by daoTypeSpec
 *
 * @param daoTypeSpec The TypeSpec containing the FunSpecs for this DAO
 * @param daoClassName Classname for the abstract DAO class
 * @param processingEnv processing environment
 * @param pagingBoundaryCallbackEnabled true/false : whether or not an Android paging boundary
 * callback will be generated
 * @param isAlwaysSqlite true if the function being generated will always run on SQLite (eg
 * on Android), false otherwise (e.g. JDBC server)
 *
 */
fun FileSpec.Builder.addDaoRepoType(
    daoTypeSpec: TypeSpec,
    daoClassName: ClassName,
    processingEnv: ProcessingEnvironment,
    target: DoorTarget,
    allKnownEntityTypesMap: Map<String, TypeElement>,
    pagingBoundaryCallbackEnabled: Boolean = false,
    isAlwaysSqlite: Boolean = false,
    extraConstructorParams: List<ParameterSpec> = listOf(),
): FileSpec.Builder {

    addType(TypeSpec.classBuilder("${daoTypeSpec.name}$SUFFIX_REPOSITORY2")
            .addProperty(PropertySpec.builder("_db", DoorDatabase::class)
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
            .superclass(daoClassName)
            .addAnnotation(AnnotationSpec.builder(Suppress::class)
                    .addMember("%S, %S, %S", "REDUNDANT_PROJECTION", "LocalVariableName",
                        "ClassName")
                    .build())
            .primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter("_db", DoorDatabase::class)
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
                daoTypeSpec.funSpecs.forEach {
                    addDaoRepoFun(it, daoClassName.simpleName, processingEnv, target,
                            allKnownEntityTypesMap, pagingBoundaryCallbackEnabled, isAlwaysSqlite)
                }
            }
            .build())

    return this
}

/**
 * Add a repo implementation of the given DAO FunSpec
 * @param daoFunSpec the function spec for which an implementation is being generated
 * @param daoName the name of the DAO class (simple name e.g. SomeDao)
 * @param processingEnv processing environment
 * @param pagingBoundaryCallbackEnabled true if an Android pagingboundarycallback is being
 * generated, false otherwise
 * @param isAlwaysSqlite true if the function will always run on SQLite, false otherwise
 */
fun TypeSpec.Builder.addDaoRepoFun(
    daoFunSpec: FunSpec,
    daoName: String,
    processingEnv: ProcessingEnvironment,
    doorTarget: DoorTarget,
    allKnownEntityTypesMap: Map<String, TypeElement>,
    pagingBoundaryCallbackEnabled: Boolean,
    isAlwaysSqlite: Boolean = false
) : TypeSpec.Builder {

    var repoMethodType = daoFunSpec.getAnnotationSpec(Repository::class.java)
            ?.memberToString(memberName = "methodType")?.toInt() ?: Repository.METHOD_AUTO

    if(repoMethodType == Repository.METHOD_AUTO) {
        repoMethodType = Repository.METHOD_DELEGATE_TO_DAO
    }

    var generateBoundaryCallback = false
    val returnTypeVal = daoFunSpec.returnType

    if(pagingBoundaryCallbackEnabled
            && returnTypeVal is ParameterizedTypeName
            && returnTypeVal.rawType == DoorDataSourceFactory::class.asClassName()) {
        generateBoundaryCallback = true
        repoMethodType = Repository.METHOD_DELEGATE_TO_DAO
    }

    addFunction(daoFunSpec.toBuilder()
            .removeAbstractModifier()
            .removeAnnotations()
            .addModifiers(KModifier.OVERRIDE)
            .addCode(CodeBlock.builder().apply {
                when(repoMethodType) {
                    Repository.METHOD_DELEGATE_TO_DAO -> {
                        if(generateBoundaryCallback) {
                            addBoundaryCallbackCode(daoFunSpec, daoName, processingEnv)
                        }else {
                            addRepoDelegateToDaoCode(daoFunSpec, isAlwaysSqlite, processingEnv
                            )
                        }

                    }
                    Repository.METHOD_DELEGATE_TO_WEB -> {
                        //check that this is http accessible, if not, emit error
                        if(!daoFunSpec.hasAnnotation(RepoHttpAccessible::class.java))
                            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR,
                                "$daoName#${daoFunSpec.name} uses delegate to web, but is not marked as http accessible")

                        if(doorTarget == DoorTarget.JS && !daoFunSpec.isSuspended) {
                            add("throw %T(%S)\n", ClassName("kotlin", "IllegalStateException"),
                                "Synchronous HTTP is not supported on Door/Javascript!")
                        }else {
                            addDelegateToWebCode(daoFunSpec, daoName)
                        }
                    }

                }
            }.build())
            .build())

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
    daoFunSpec: FunSpec,
    isAlwaysSqlite: Boolean,
    processingEnv: ProcessingEnvironment
) : CodeBlock.Builder{

    if(daoFunSpec.hasReturnType)
        add("val _result = ")

    add("_dao.${daoFunSpec.name}(")
            .add(daoFunSpec.parameters.joinToString { it.name })
            .add(")\n")



    if(daoFunSpec.hasReturnType) {
        add("return _result\n")
    }

    return this
}

/**
 * Generate the Android paging boundary callback implementation
 */
fun CodeBlock.Builder.addBoundaryCallbackCode(daoFunSpec: FunSpec, daoName: String,
    processingEnv: ProcessingEnvironment) : CodeBlock.Builder{

    val boundaryCallbackFunSpec = daoFunSpec.toBuilder()
            .addParameter(PARAM_NAME_LIMIT, INT)
            .build()
    add("val _dataSource = ").addDelegateFunctionCall("_dao", daoFunSpec).add("\n")
    add("val $PARAM_NAME_LIMIT = 50\n")

    val unwrappedComponentType = daoFunSpec.returnType?.unwrapQueryResultComponentType()
            ?: throw IllegalArgumentException("${daoFunSpec.name} on $daoName has no return type")
    add("$DATASOURCEFACTORY_TO_BOUNDARYCALLBACK_VARNAME[_dataSource] = %T(_loadHelper)\n",
            BOUNDARY_CALLBACK_CLASSNAME.parameterizedBy(unwrappedComponentType))
    add("return _dataSource\n")

    return this
}

fun CodeBlock.Builder.addDelegateToWebCode(daoFunSpec: FunSpec, daoName: String) : CodeBlock.Builder {
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
class DbProcessorRepository: AbstractDbProcessor() {

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val dbs = roundEnv.getElementsAnnotatedWith(Database::class.java)


        for(dbTypeEl in dbs) {
            val hasRepos = (dbTypeEl as TypeElement).dbHasRepositories(processingEnv)

            if(!hasRepos)
                continue //This database has no repositories - skip it

            FileSpec.builder(dbTypeEl.packageName, "${dbTypeEl.simpleName}$SUFFIX_REPOSITORY2")
                    .addDbRepoType(dbTypeEl, processingEnv,
                        syncDaoMode = REPO_SYNCABLE_DAO_CONSTRUCT,
                        addDbVersionProp = true,
                        overrideDataSourceProp = true,
                        overrideWrapDbForTransaction = true,
                        target = DoorTarget.JVM)
                    .build()
                    .writeToDirsFromArg(OPTION_JVM_DIRS)

            FileSpec.builder(dbTypeEl.packageName, "${dbTypeEl.simpleName}$SUFFIX_REPOSITORY2")
                .addDbRepoType(dbTypeEl, processingEnv,
                    syncDaoMode = REPO_SYNCABLE_DAO_CONSTRUCT,
                    addDbVersionProp = true,
                    overrideDataSourceProp = true,
                    overrideWrapDbForTransaction = false,
                    target = DoorTarget.JS)
                .build()
                .writeToDirsFromArg(OPTION_JS_OUTPUT)

            FileSpec.builder(dbTypeEl.packageName, "${dbTypeEl.simpleName}$SUFFIX_REPOSITORY2")
                    .addDbRepoType(dbTypeEl, processingEnv,
                        syncDaoMode = REPO_SYNCABLE_DAO_FROMDB, overrideClearAllTables = false,
                        overrideSyncDao = true, overrideOpenHelper = true,
                        overrideKtorHelpers = true,
                        target = DoorTarget.ANDROID)
                    .build()
                    .writeToDirsFromArg(OPTION_ANDROID_OUTPUT)
            dbTypeEl.allDbEntities(processingEnv)
                    .filter { it.entityHasAttachments }.forEach { entityEl ->
                        FileSpec.builder(entityEl.packageName, "${entityEl.simpleName}$SUFFIX_ENTITY_WITH_ATTACHMENTS_ADAPTER")
                                .addEntityWithAttachmentAdapterType(entityEl, processingEnv)
                                .addAsEntityWithAttachmentAdapterExtensionFun(entityEl)
                                .build()
                                .writeToDirsFromArg(listOf(OPTION_JVM_DIRS, OPTION_ANDROID_OUTPUT, OPTION_JS_OUTPUT))
            }
        }

        val daos = roundEnv.getElementsAnnotatedWith(Dao::class.java)

        for(daoElement in daos) {
            val daoTypeEl = daoElement as TypeElement
            if(daoTypeEl.isDaoWithRepository) {
                val targets = mapOf(DoorTarget.JVM to OPTION_JVM_DIRS, DoorTarget.JS to OPTION_JS_OUTPUT)
                targets.forEach { target ->
                    FileSpec.builder(daoElement.packageName,
                        "${daoTypeEl.simpleName}$SUFFIX_REPOSITORY2")
                        .addDaoRepoType(daoTypeEl.asTypeSpecStub(processingEnv),
                            daoTypeEl.asClassName(), processingEnv, target.key,
                            allKnownEntityTypesMap = allKnownEntityTypesMap)
                        .build()
                        .writeToDirsFromArg(target.value)
                }

                FileSpec.builder(daoElement.packageName,
                        "${daoTypeEl.simpleName}$SUFFIX_REPOSITORY2")
                        .addDaoRepoType(daoTypeEl.asTypeSpecStub(processingEnv),
                                daoTypeEl.asClassName(), processingEnv, DoorTarget.ANDROID,
                                allKnownEntityTypesMap = allKnownEntityTypesMap,
                                pagingBoundaryCallbackEnabled = false,
                                isAlwaysSqlite = true)
                        .build()
                        .writeToDirsFromArg(OPTION_ANDROID_OUTPUT)
            }
        }

        return true
    }


    companion object {
        //including the underscore as it should
        const val SUFFIX_REPOSITORY2 = "_Repo"

        const val SUFFIX_ENTITY_WITH_ATTACHMENTS_ADAPTER = "_EwaAdapter"

        /**
         * When creating a repository, the Syncable DAO is constructed (JDBC). This is because
         * the database itself cannot have fields or method signatures that are themselves generated
         * classes
         */
        const val REPO_SYNCABLE_DAO_CONSTRUCT = 1

        /**
         * When creatin ga repository, the Syncable DAO is obtained from the database. This is done
         * on Room on Android, where the database class is slightly modified and all DAOs must come
         * from the database object
         */
        const val REPO_SYNCABLE_DAO_FROMDB = 2

        /**
         * A static string which is generated for the database name part of the http path, which is
         * passed from the database repository to the DAO repository so it can use the correct http
         * path e.g. endpoint/dbname/daoname
         */
        const val DB_NAME_VAR = "_DB_NAME"

        const val DATASOURCEFACTORY_TO_BOUNDARYCALLBACK_VARNAME = "_dataSourceFactoryToBoundaryCallbackMap"

        val BOUNDARY_CALLBACK_CLASSNAME = ClassName("com.ustadmobile.door",
                "RepositoryBoundaryCallback")

        val BOUNDARY_CALLBACK_MAP_CLASSNAME = WeakHashMap::class.asClassName().parameterizedBy(
                DoorDataSourceFactory::class.asClassName().parameterizedBy(INT, STAR),
                BOUNDARY_CALLBACK_CLASSNAME.parameterizedBy(STAR))

    }



}