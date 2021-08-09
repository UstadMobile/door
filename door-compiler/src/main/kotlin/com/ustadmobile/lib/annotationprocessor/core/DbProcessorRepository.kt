package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.*
import com.ustadmobile.door.annotation.Repository
import com.ustadmobile.door.annotation.SyncableEntity
import com.ustadmobile.door.attachments.EntityWithAttachment
import com.ustadmobile.door.daos.*
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_ANDROID_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_JVM_DIRS
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorJdbcKotlin.Companion.SUFFIX_JDBC_KT2
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorRepository.Companion.BOUNDARY_CALLBACK_CLASSNAME
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorRepository.Companion.DATASOURCEFACTORY_TO_BOUNDARYCALLBACK_VARNAME
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorRepository.Companion.SUFFIX_ENTITY_WITH_ATTACHMENTS_ADAPTER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorRepository.Companion.SUFFIX_REPOSITORY2
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorSync.Companion.CLASSNAME_SYNC_HELPERENTITIES_DAO
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorSync.Companion.SUFFIX_SYNCDAO_ABSTRACT
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorSync.Companion.SUFFIX_SYNCDAO_IMPL
import io.ktor.client.*
import kotlinx.coroutines.GlobalScope
import java.util.*
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/**
 * Where this TypeElement represents a Database class, this is the property name which should be
 * used for the property name for the SyncDao repo class. It will always be _DatabaseName_SyncDao
 */
private val TypeElement.syncDaoPropName: String
    get() = "_${this.simpleName}$SUFFIX_SYNCDAO_ABSTRACT"

/**
 * Where this TypeElement represents a Database class, this is the ClassName which should be used
 * for the abstract SyncDao. It will always be in the form of DatabaseName_SyncDao
 */
private val TypeElement.abstractSyncDaoClassName: ClassName
    get() = asClassNameWithSuffix(SUFFIX_SYNCDAO_ABSTRACT)

/**
 * Generate the table id map of entity names (strings) to the table id as per the syncableentity
 * annotation
 */
fun TypeSpec.Builder.addTableIdMapProperty(dbTypeElement: TypeElement, processingEnv: ProcessingEnvironment) : TypeSpec.Builder {
    addProperty(PropertySpec.builder("TABLE_ID_MAP",
            Map::class.asClassName().parameterizedBy(String::class.asClassName(), INT))
            .initializer(CodeBlock.builder()
                    .add("mapOf(")
                    .apply {
                        dbTypeElement.allSyncableDbEntities(processingEnv).forEachIndexed { index, syncableEl ->
                            if(index > 0)
                                add(",")

                            val syncableEntityInfo = SyncableEntityInfo(syncableEl.asClassName(),
                                    processingEnv)
                            add("%S to %L\n", syncableEntityInfo.syncableEntity.simpleName,
                                    syncableEntityInfo.tableId)
                        }
                    }
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
    overrideDataSourceProp: Boolean = false
): FileSpec.Builder {
    addType(TypeSpec.classBuilder(dbTypeElement.asClassNameWithSuffix(SUFFIX_REPOSITORY2))
            .superclass(dbTypeElement.asClassName())
            .apply {
                if(dbTypeElement.isDbSyncable(processingEnv)) {
                    addSuperinterface(DoorDatabaseSyncRepository::class)
                }else {
                    addSuperinterface(DoorDatabaseRepository::class)
                }
            }
            .addAnnotation(AnnotationSpec.builder(Suppress::class)
                    .addMember("%S, %S, %S, %S", "LocalVariableName", "PropertyName", "FunctionName",
                            "ClassName")
                    .build())
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("db", dbTypeElement.asClassName())
                .addParameter("dbUnwrapped", dbTypeElement.asClassName())
                .addParameter("config", RepositoryConfig::class)
                .build())
            .applyIf(overrideDataSourceProp) {
                addDataSourceProperty("db")
            }
            .addProperty(PropertySpec.builder("config", RepositoryConfig::class)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("config")
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
            .addProperty(PropertySpec.builder("dbPath", String::class)
                    .getter(FunSpec.getterBuilder().addCode("return ${DbProcessorRepository.DB_NAME_VAR}\n").build())
                    .addModifiers(KModifier.OVERRIDE)
                    .build())
            .addProperty(PropertySpec.builder("_updateNotificationManager",
                    ServerUpdateNotificationManager::class.asClassName().copy(nullable = true))
                    .initializer("config.updateNotificationManager")
                    .build())
            .addProperty(PropertySpec.builder("_repositoryHelper", RepositoryHelper::class)
                    .initializer("%T(%M(%S))", RepositoryHelper::class,
                            MemberName("kotlinx.coroutines", "newSingleThreadContext"),
                            "Repo-${dbTypeElement.simpleName}")
                    .build())
            .addProperty(PropertySpec.builder("_clientSyncManager",
                    ClientSyncManager::class.asClassName().copy(nullable = true))
                    .initializer(CodeBlock.builder().beginControlFlow("if(config.useClientSyncManager)")
                            .add("%T(this, _db.%M(), _repositoryHelper.connectivityStatus, config.httpClient)\n",
                                    ClientSyncManager::class, MemberName("com.ustadmobile.door.ext", "dbSchemaVersion"))
                            .nextControlFlow("else")
                            .add("null\n")
                            .endControlFlow()
                            .build())
                    .build())
            .addProperty(PropertySpec.builder("tableIdMap",
                    Map::class.asClassName().parameterizedBy(String::class.asClassName(), INT))
                    .getter(FunSpec.getterBuilder().addCode("return TABLE_ID_MAP\n").build())
                    .addModifiers(KModifier.OVERRIDE)
                    .build())
            .addFunction(FunSpec.builder("clearAllTables")
                    .addModifiers(KModifier.OVERRIDE)
                    .addCode("throw %T(%S)\n", IllegalAccessException::class, "Cannot use a repository to clearAllTables!")
                    .build())
            .applyIf(dbTypeElement.isDbSyncable(processingEnv)) {
                addFunction(FunSpec.builder("invalidateAllTables")
                        .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                        .addCode("_clientSyncManager?.invalidateAllTables()\n")
                        .build())
            }

            .addRepositoryHelperDelegateCalls("_repositoryHelper",
                    "_clientSyncManager")
            .applyIf(overrideClearAllTables) {
                addFunction(FunSpec.builder("createAllTables")
                        .addModifiers(KModifier.OVERRIDE)
                        .addCode("throw %T(%S)\n",
                                IllegalAccessException::class,
                                "Cannot use a repository to createAllTables!")
                        .build())
            }
            .applyIf(overrideSyncDao) {
                addFunction(FunSpec.builder("_syncDao")
                        .addModifiers(KModifier.OVERRIDE)
                        .addCode("return _db._syncDao()\n")
                        .returns(dbTypeElement.abstractSyncDaoClassName)
                        .build())
            }
            .applyIf(overrideSyncDao && dbTypeElement.isDbSyncable(processingEnv)){
                addFunction(FunSpec.builder("_syncHelperEntitiesDao")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(ClassName("com.ustadmobile.door.daos",
                                "SyncHelperEntitiesDao"))
                        .addCode("return _db._syncHelperEntitiesDao()\n")
                        .build())
            }
            .applyIf(overrideOpenHelper) {
                addRoomCreateInvalidationTrackerFunction()
                addRoomDatabaseCreateOpenHelperFunction()
            }
            .applyIf(addDbVersionProp) {
                addDbVersionProperty(dbTypeElement)
            }
            .applyIf(dbTypeElement.isDbSyncable(processingEnv) &&
                    syncDaoMode == DbProcessorRepository.REPO_SYNCABLE_DAO_CONSTRUCT) {
                addProperty(PropertySpec.builder("_syncDao",
                                dbTypeElement.abstractSyncDaoClassName)
                        .delegate(CodeBlock.builder().beginControlFlow("lazy")
                                .add("%T(_db)\n", dbTypeElement.asClassNameWithSuffix(SUFFIX_SYNCDAO_IMPL))
                                .endControlFlow().build())
                        .build())
                addProperty(PropertySpec.builder("_syncHelperEntitiesDao",
                            CLASSNAME_SYNC_HELPERENTITIES_DAO)
                        .delegate(CodeBlock.builder().beginControlFlow("lazy")
                                .add("%T(db)\n", CLASSNAME_SYNC_HELPERENTITIES_DAO.withSuffix(SUFFIX_JDBC_KT2))
                                .endControlFlow()
                                .build())
                        .build())
            }.applyIf(dbTypeElement.isDbSyncable(processingEnv) &&
                    syncDaoMode == DbProcessorRepository.REPO_SYNCABLE_DAO_FROMDB) {
                addProperty(PropertySpec.builder("_syncDao",
                        dbTypeElement.abstractSyncDaoClassName)
                        .getter(FunSpec.getterBuilder()
                                .addCode("return _db._syncDao()\n")
                                .build())
                        .build())
                addProperty(PropertySpec.builder("_syncHelperEntitiesDao",
                        CLASSNAME_SYNC_HELPERENTITIES_DAO)
                        .getter(FunSpec.getterBuilder()
                                .addCode("return _db._syncHelperEntitiesDao()\n")
                                .build())
                        .build())
            }.applyIf(dbTypeElement.isDbSyncable(processingEnv)) {
                addProperty(PropertySpec.builder("syncHelperEntitiesDao",
                        ISyncHelperEntitiesDao::class)
                    .addModifiers(KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder()
                            .addCode("return _syncHelperEntitiesDao")
                            .build())
                    .build())
                addProperty(PropertySpec.builder("clientId", INT)
                        .getter(FunSpec.getterBuilder().addCode("return config.nodeId\n").build())
                        .addModifiers(KModifier.OVERRIDE)
                        .build())
                addProperty(PropertySpec.builder("master", BOOLEAN)
                        .addModifiers(KModifier.OVERRIDE)
                        .getter(FunSpec.getterBuilder().addCode("return _db.master").build())
                        .build())
                addProperty(PropertySpec.builder(dbTypeElement.syncDaoPropName,
                            dbTypeElement.asClassNameWithSuffix("$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_REPOSITORY2"))
                        .delegate(CodeBlock.builder().beginControlFlow("lazy")
                                .add("%T(_db, this, _syncDao, _httpClient, clientId, _endpoint," +
                                        " ${DbProcessorRepository.DB_NAME_VAR}, config.attachmentsDir," +
                                        " _syncDao) ",
                                        dbTypeElement
                                                .asClassNameWithSuffix("$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_REPOSITORY2"))
                                .endControlFlow().build())
                        .build())
                addRepoSyncFunction(dbTypeElement, processingEnv)
                addRepoDispatchUpdatesFunction(dbTypeElement, processingEnv)
                val syncRepoVarName = "_${dbTypeElement.simpleName}$SUFFIX_SYNCDAO_ABSTRACT"

                dbTypeElement.allDbEntities(processingEnv)
                        .filter { it.hasAnnotation(SyncableEntity::class.java) }
                        .forEach { entityType ->
                            addRepoSyncEntityFunction(entityType, syncRepoVarName)
                        }

                addProperty(PropertySpec.builder("_pkManager",
                        DoorPrimaryKeyManager::class)
                        .initializer("%T(TABLE_ID_MAP.values)", DoorPrimaryKeyManager::class)
                        .build())

                addFunction(FunSpec.builder("nextId")
                        .addParameter("tableId", INT)
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(LONG)
                        .addCode("return _pkManager.nextId(tableId)\n")
                        .build())

                addFunction(FunSpec.builder("nextIdAsync")
                        .addParameter("tableId", INT)
                        .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                        .returns(LONG)
                        .addCode("return _pkManager.nextIdAsync(tableId)\n")
                        .build())

            }
            .apply {
                dbTypeElement.allDbClassDaoGetters(processingEnv).forEach { daoGetter ->
                    addRepoDbDaoAccessor(daoGetter, overrideKtorHelpers, processingEnv)
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
private fun TypeSpec.Builder.addRepoDbDaoAccessor(daoGetter: ExecutableElement,
                                          overrideKtorHelpers: Boolean,
                                          processingEnv: ProcessingEnvironment) : TypeSpec.Builder{
    val daoTypeEl = daoGetter.returnType.asTypeElement(processingEnv)
            ?: throw IllegalArgumentException("Dao getter has no return type")
    if(!daoTypeEl.hasAnnotation(Repository::class.java)) {
        addAccessorOverride(daoGetter, CodeBlock.of("throw %T(%S)\n",
                IllegalStateException::class,
                "${daoTypeEl.simpleName} is not annotated with @Repository"))
        return this
    }

    val daoTypeSpec = daoTypeEl.asTypeSpecStub(processingEnv)
    val daoHasSyncableEntities = daoTypeSpec.isDaoWithSyncableEntitiesInSelectResults(processingEnv)

    val syncDaoParam = if(daoHasSyncableEntities) {
        ", _syncDao"
    }else {
        ""
    }

    addProperty(PropertySpec.builder("_${daoTypeEl.simpleName}",
                daoTypeEl.asClassNameWithSuffix(SUFFIX_REPOSITORY2))
            .delegate(CodeBlock.builder().beginControlFlow("lazy")
                    .add("%T(_db, this, _db.%L, _httpClient, clientId, _endpoint, ${DbProcessorRepository.DB_NAME_VAR}, " +
                            "config.attachmentsDir $syncDaoParam) ",
                            daoTypeEl.asClassNameWithSuffix(SUFFIX_REPOSITORY2),
                            daoGetter.makeAccessorCodeBlock())
                    .endControlFlow()
                    .build())
            .build())

    addAccessorOverride(daoGetter, CodeBlock.of("return  _${daoTypeEl.simpleName}"))

    if(overrideKtorHelpers) {
        listOf("Master", "Local").forEach {suffix ->
            val ktorHelperClassName = daoTypeEl.asClassNameWithSuffix(
                    "${DbProcessorKtorServer.SUFFIX_KTOR_HELPER}$suffix")
            addFunction(FunSpec.builder("_${ktorHelperClassName.simpleName}")
                    .returns(ktorHelperClassName)
                    .addModifiers(KModifier.OVERRIDE)
                    .addCode("throw %T(%S)", IllegalAccessException::class,
                            "Cannot access KTOR HTTP Helper from Repository")
                    .build())
        }
    }

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
fun FileSpec.Builder.addDaoRepoType(daoTypeSpec: TypeSpec,
                                    daoClassName: ClassName,
                                    processingEnv: ProcessingEnvironment,
                                    allKnownEntityTypesMap: Map<String, TypeElement>,
                                    pagingBoundaryCallbackEnabled: Boolean = false,
                                    isAlwaysSqlite: Boolean = false,
                                    extraConstructorParams: List<ParameterSpec> = listOf(),
                                    syncHelperClassName: ClassName = daoClassName.withSuffix("_SyncHelper")): FileSpec.Builder {

    addType(TypeSpec.classBuilder("${daoTypeSpec.name}$SUFFIX_REPOSITORY2")
            .addProperty(PropertySpec.builder("_db", DoorDatabase::class)
                    .initializer("_db").build())
            .addProperty(PropertySpec.builder("_repo", DoorDatabaseRepository::class)
                    .initializer("_repo").build())
            .addProperty(PropertySpec.builder("_dao",
                    daoClassName).initializer("_dao").build())
            .addProperty(PropertySpec.builder("_httpClient",
                    HttpClient::class).initializer("_httpClient").build())
            .addProperty(PropertySpec.builder("_clientId", Int::class)
                    .initializer("_clientId").build())
            .addProperty(PropertySpec.builder("_endpoint", String::class)
                    .initializer("_endpoint").build())
            .addProperty(PropertySpec.builder("_dbPath", String::class)
                    .initializer("_dbPath").build())
            .addProperty(PropertySpec.builder("_attachmentsDir", String::class)
                    .initializer("_attachmentsDir").build())
            .applyIf(daoTypeSpec.isDaoWithSyncableEntitiesInSelectResults(processingEnv)) {
                addProperty(PropertySpec.builder("_syncHelper",
                        syncHelperClassName)
                        .initializer("_syncHelper")
                        .build())
            }
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
                    .addParameter("_clientId", Int::class)
                    .addParameter("_endpoint", String::class)
                    .addParameter("_dbPath", String::class)
                    .addParameter("_attachmentsDir", String::class)
                    .apply {
                        takeIf { extraConstructorParams.isNotEmpty() }?.addParameters(extraConstructorParams)
                    }
                    .applyIf(daoTypeSpec.isDaoWithSyncableEntitiesInSelectResults(processingEnv)) {
                        addParameter("_syncHelper", syncHelperClassName)
                    }
                    .build())
            //TODO: Ideally check and see if any of the return function types are DataSource.Factory
            .applyIf(pagingBoundaryCallbackEnabled &&
                    daoTypeSpec.isDaoWithSyncableEntitiesInSelectResults(processingEnv)) {
                addProperty(PropertySpec.builder(
                        DATASOURCEFACTORY_TO_BOUNDARYCALLBACK_VARNAME,
                        DbProcessorRepository.BOUNDARY_CALLBACK_MAP_CLASSNAME)
                        .initializer("%T()", WeakHashMap::class)
                        .build())
                addSuperinterface(ClassName("com.ustadmobile.door",
                    "DoorBoundaryCallbackProvider"))
                addFunction(FunSpec.builder("getBoundaryCallback")
                        .addAnnotation(AnnotationSpec.builder(Suppress::class)
                                .addMember("%S", "UNCHECKED_CAST")
                                .build())
                        .addTypeVariable(TypeVariableName("T"))
                        .addParameter("dataSource",
                                DoorDataSourceFactory::class.asClassName().parameterizedBy(INT,
                                        TypeVariableName("T")))
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(BOUNDARY_CALLBACK_CLASSNAME
                                .parameterizedBy(TypeVariableName("T")).copy(nullable = true))
                        .addCode("return ${DbProcessorRepository.DATASOURCEFACTORY_TO_BOUNDARYCALLBACK_VARNAME}[dataSource] as %T\n",
                                DbProcessorRepository.BOUNDARY_CALLBACK_CLASSNAME
                                .parameterizedBy(TypeVariableName("T")).copy(nullable = true))
                        .build())

            }
            .apply {
                daoTypeSpec.funSpecs.forEach {
                    addDaoRepoFun(it, daoClassName.simpleName, processingEnv,
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
fun TypeSpec.Builder.addDaoRepoFun(daoFunSpec: FunSpec,
                                   daoName: String,
                                   processingEnv: ProcessingEnvironment,
                                   allKnownEntityTypesMap: Map<String, TypeElement>,
                                   pagingBoundaryCallbackEnabled: Boolean,
                                   isAlwaysSqlite: Boolean = false) : TypeSpec.Builder {

    var repoMethodType = daoFunSpec.getAnnotationSpec(Repository::class.java)
            ?.memberToString(memberName = "methodType")?.toInt() ?: Repository.METHOD_AUTO

    if(repoMethodType == Repository.METHOD_AUTO) {
        repoMethodType = when {
            daoFunSpec.isQueryWithSyncableResults(processingEnv) -> Repository.METHOD_SYNCABLE_GET
            else -> Repository.METHOD_DELEGATE_TO_DAO
        }
    }

    var generateBoundaryCallback = false
    val returnTypeVal = daoFunSpec.returnType

    if(pagingBoundaryCallbackEnabled
            && repoMethodType == Repository.METHOD_SYNCABLE_GET
            && returnTypeVal is ParameterizedTypeName
            && returnTypeVal.rawType == DoorDataSourceFactory::class.asClassName()) {
        generateBoundaryCallback = true
        repoMethodType = Repository.METHOD_DELEGATE_TO_DAO
    }

    addFunction(daoFunSpec.toBuilder()
            .removeAbstractModifier()
            .addModifiers(KModifier.OVERRIDE)
            .addCode(CodeBlock.builder().apply {
                when(repoMethodType) {
                    Repository.METHOD_SYNCABLE_GET -> {
                        addRepositoryGetSyncableEntitiesCode(daoFunSpec,
                                daoName, processingEnv,
                                addReturnDaoResult = !generateBoundaryCallback)
                    }
                    Repository.METHOD_DELEGATE_TO_DAO -> {
                        if(generateBoundaryCallback) {
                            addBoundaryCallbackCode(daoFunSpec, daoName, processingEnv)
                        }else {
                            addRepoDelegateToDaoCode(daoFunSpec, isAlwaysSqlite, processingEnv,
                                    allKnownEntityTypesMap)
                        }

                    }
                    Repository.METHOD_DELEGATE_TO_WEB -> {
                        addDelegateToWebCode(daoFunSpec, daoName)
                    }

                }
            }.build())
            .build())

    return this
}

/**
 * Add code which fetches any new syncable entities from the server and returns the results from the
 * DAO.
 *
 * @param daoFunSpec the DAO function spec for which this code is being generated
 * @param daoName the simple name of the DAO class
 * @param processingEnv processing environment
 * @param syncHelperDaoVarName the variable name of the sync helper dao
 * @param addReturnDaoResult true to add a return statement to the end of the code
 * @param generateGlobalScopeLaunchBlockForLiveDataTypes true to put the http fetch for functions
 * that return a LiveData object in a GlobalScope.launch so they run asynchronously. True by default
 * @param autoRetryEmptyMirrorResult will be removed
 * @param receiveCountVarName if not null, a variable that will hold a count of how many entities
 * are received
 */
fun CodeBlock.Builder.addRepositoryGetSyncableEntitiesCode(daoFunSpec: FunSpec, daoName: String,
                                                           processingEnv: ProcessingEnvironment,
                                                           syncHelperDaoVarName: String = "_syncHelper",
                                                           addReturnDaoResult: Boolean  = true,
                                                           generateGlobalScopeLaunchBlockForLiveDataTypes: Boolean = true,
                                                           autoRetryEmptyMirrorResult: Boolean = false,
                                                           receiveCountVarName: String? = null) : CodeBlock.Builder {

    val isLiveDataOrDataSourceFactory = daoFunSpec.returnType?.isDataSourceFactoryOrLiveData() == true
    val isLiveData = daoFunSpec.returnType?.isLiveData() == true

    if(isLiveDataOrDataSourceFactory && addReturnDaoResult) {
        add("val _daoResult = ").addDelegateFunctionCall("_dao", daoFunSpec).add("\n")
    }

    if(isLiveDataOrDataSourceFactory && generateGlobalScopeLaunchBlockForLiveDataTypes) {
        beginControlFlow("%T.%M", GlobalScope::class,
                MemberName("kotlinx.coroutines", "launch"))
        beginControlFlow("try")
    }

    //Create the loadhelper that would actually run the request
    val liveDataLoadHelperArg = if(isLiveData) "autoRetryOnEmptyLiveData=_daoResult," else ""
    beginControlFlow("val _loadHelper = %T(_repo,·" +
            "autoRetryEmptyMirrorResult·=·$autoRetryEmptyMirrorResult,·$liveDataLoadHelperArg·" +
            "uri·=·%S)", RepositoryLoadHelper::class, "$daoName/${daoFunSpec.name}")
    add("_endpointToTry -> \n")

    add("val _httpResult = ")
    addKtorRequestForFunction(daoFunSpec, dbPathVarName = "_dbPath", daoName = daoName,
        httpEndpointVarName = "_endpointToTry", addClientIdHeaderVar = "_clientId")
    addReplaceSyncableEntitiesIntoDbCode("_httpResult",
            daoFunSpec.returnType!!.unwrapLiveDataOrDataSourceFactory(), processingEnv,
                daoName = daoName, isInSuspendContext = true)
    if(receiveCountVarName != null) {
        add("$receiveCountVarName += _httpResult.size\n")
    }

    //end the LoadHelper block
    add("_httpResult\n")
    endControlFlow()

    if(isLiveDataOrDataSourceFactory && generateGlobalScopeLaunchBlockForLiveDataTypes) {
        add("_loadHelper.doRequest()\n")
        nextControlFlow("catch(_e: %T)", Exception::class)
        add("%M(%S)\n", MemberName("kotlin.io", "println"), "Caught doRequest exception:")
        endControlFlow()
        endControlFlow()
    }

    if(addReturnDaoResult) {
        if(!isLiveDataOrDataSourceFactory) {
            //use the repoloadhelper to actually run the request and get the result
            add("var _daoResult: %T\n", daoFunSpec.returnType?.unwrapLiveDataOrDataSourceFactory())
                    .beginControlFlow("do"
                    ).applyIf(KModifier.SUSPEND !in daoFunSpec.modifiers) {
                        beginControlFlow("%M",
                                MemberName("kotlinx.coroutines", "runBlocking"))
                    }
                    .beginControlFlow("try")
                    .add("_loadHelper.doRequest()\n")
                    .nextControlFlow("catch(_e: %T)", Exception::class)
                    .add("%M(%S)", MemberName("kotlin.io", "println"), "Caught doRequest exception: \\\$_e")
                    .endControlFlow()
                    .applyIf(KModifier.SUSPEND !in daoFunSpec.modifiers) {
                        endControlFlow()
                    }
                    .add("_daoResult = ").addDelegateFunctionCall("_dao", daoFunSpec).add("\n")
                    .endControlFlow()
                    .add("while(_loadHelper.shouldTryAnotherMirror())\n")
                    .add("return _daoResult\n")
        }else {
            add("return _daoResult\n")
        }
    }

    return this
}


/**
 * Add code that will handle receiving new syncable entities from the server. The syncable entities
 * should only be those that are new to the client. The entities will be inserted using a replace
 * function on the SyncHelper, and then an http request will be made to the server to acknowledge
 * receipt of the entities.
 */
fun CodeBlock.Builder.addReplaceSyncableEntitiesIntoDbCode(resultVarName: String, resultType: TypeName,
                                                           processingEnv: ProcessingEnvironment,
                                                           daoName: String,
                                                           isInSuspendContext: Boolean,
                                                           syncHelperDaoVarName: String = "_syncHelper") : CodeBlock.Builder{
    val componentType = resultType.unwrapQueryResultComponentType()
    if(componentType !is ClassName)
        return this


    //Block at the end which will run all inserts of newly received entities
    val transactionCodeBlock = CodeBlock.builder()

    val sendTrkEntitiesCodeBlock = CodeBlock.builder()

    componentType.findAllSyncableEntities(processingEnv).forEach {
        val sEntityInfo = SyncableEntityInfo(it.value, processingEnv)

        val replaceEntityFnName ="_replace${sEntityInfo.syncableEntity.simpleName}"

        val accessorVarName = "_se${sEntityInfo.syncableEntity.simpleName}"
        add("val $accessorVarName = $resultVarName")
        val entityTypeEl = it.value.asTypeElement(processingEnv)

        if(resultType.isListOrArray()) {
            it.key.forEach {embedVarName ->
                beginControlFlow(".mapNotNull ")
                add("it.$embedVarName", it.value.copy(nullable = true))
                endControlFlow()
            }

            if(it.key.isEmpty() && it.value != sEntityInfo.syncableEntity) {
                add(".map { it as %T }", sEntityInfo.syncableEntity)
            }

            add("\n")

            //download attachments if this entity type has attachments
            if(entityTypeEl != null && entityTypeEl.entityHasAttachments == true) {
                if(!isInSuspendContext)
                    beginRunBlockingControlFlow()

                add("_repo.%M($accessorVarName}.map·{·it.%M()·})\n",
                    MemberName("com.ustadmobile.door.attachments", "downloadAttachments"),
                    MemberName(entityTypeEl.packageName, "asEntityWithAttachment"))

                if(!isInSuspendContext)
                    endControlFlow()
            }


            transactionCodeBlock.add("${syncHelperDaoVarName}.$replaceEntityFnName($accessorVarName)\n")
        }else {
            if(it.key.isNotEmpty())
                add("?.")

            add(it.key.joinToString (prefix = "", separator = "?.", postfix = ""))
            add("\n")

            if(entityTypeEl != null && entityTypeEl.entityHasAttachments) {
                if(!isInSuspendContext)
                    beginRunBlockingControlFlow()

                beginControlFlow("if($accessorVarName != null)")
                add("_repo.%M(listOf($accessorVarName.%M()))\n",
                        MemberName("com.ustadmobile.door.attachments", "downloadAttachments"),
                        MemberName(entityTypeEl.packageName, "asEntityWithAttachment"))
                endControlFlow()

                if(!isInSuspendContext)
                    endControlFlow()
            }

            transactionCodeBlock.
                beginControlFlow("if($accessorVarName != null)")
                    .add("${syncHelperDaoVarName}.$replaceEntityFnName(listOf($accessorVarName))\n")
                    .endControlFlow()
        }

        sendTrkEntitiesCodeBlock.beginIfNotNullOrEmptyControlFlow(accessorVarName,
                resultType.isListOrArray())
                .add("val _ackList = ")
                .apply {
                    if(!resultType.isListOrArray())
                        add("listOf($accessorVarName)")
                    else
                        add(accessorVarName)
                }
                .beginControlFlow(".map")
                .add("·%T(epk·=·it.${sEntityInfo.entityPkField.name},·" +
                        "csn·=·it.${sEntityInfo.entityMasterCsnField.name}", EntityAck::class)
                .applyIf(sEntityInfo.entityMasterCsnField.type == LONG) {
                    add(".toInt()")
                }
                .add(")\n")
                .endControlFlow()
                .add("\n")
                .add("_httpClient.%M(_ackList, _endpoint, \"${'$'}_dbPath/$daoName/_ack${it.value.simpleName}Received\", _repo)\n",
                    MemberName("com.ustadmobile.door.ext", "postEntityAck"))
                .endControlFlow()
    }

    takeIf { !isInSuspendContext }?.beginRunBlockingControlFlow()
    beginControlFlow("_db.%M",
            MemberName("com.ustadmobile.door.ext", "doorWithTransaction"))
    add(transactionCodeBlock.build())
    endControlFlow()
    takeIf { !isInSuspendContext }?.endControlFlow()

    add(sendTrkEntitiesCodeBlock.build())

    return this
}

private fun CodeBlock.Builder.beginAttachmentStorageFlow(daoFunSpec: FunSpec) {
    val entityParam = daoFunSpec.parameters.first()
    val isList = entityParam.type.isListOrArray()

    if(!daoFunSpec.isSuspended)
        beginRunBlockingControlFlow()

    if(isList)
        beginControlFlow("${entityParam.name}.forEach")
}

private fun CodeBlock.Builder.endAttachmentStorageFlow(daoFunSpec: FunSpec) {
    val entityParam = daoFunSpec.parameters.first()
    val isList = entityParam.type.isListOrArray()

    if(!daoFunSpec.isSuspended)
        endControlFlow()

    if(isList)
        endControlFlow()
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
fun CodeBlock.Builder.addRepoDelegateToDaoCode(daoFunSpec: FunSpec, isAlwaysSqlite: Boolean,
                                   processingEnv: ProcessingEnvironment,
                                   allKnownEntityTypesMap: Map<String, TypeElement>) : CodeBlock.Builder{

    var syncableEntityInfo: SyncableEntityInfo? = null

    if(daoFunSpec.hasAnyAnnotation(Update::class.java, Delete::class.java, Insert::class.java)) {
        val entityParam = daoFunSpec.parameters.first()
        val entityComponentType = entityParam.type.unwrapListOrArrayComponentType()

        if(daoFunSpec.hasAnyAnnotation(Update::class.java, Insert::class.java)
                && entityComponentType.hasAttachments(processingEnv)) {
            val isList = entityParam.type.isListOrArray()

            beginAttachmentStorageFlow(daoFunSpec)

            val entityClassName = entityComponentType as ClassName

            add("_repo.%M(%L.%M())\n",
                    MemberName("com.ustadmobile.door.attachments", "storeAttachment"),
                    if(isList) "it" else entityParam.name,
                    MemberName(entityClassName.packageName, "asEntityWithAttachment"))

            endAttachmentStorageFlow(daoFunSpec)
        }

        if(entityComponentType.hasSyncableEntities(processingEnv)) {
            syncableEntityInfo = SyncableEntityInfo(entityComponentType as ClassName, processingEnv)
            if(daoFunSpec.hasAnyAnnotation(Update::class.java)) {
                add("val _isSyncablePrimary = _db.%M\n",
                        MemberName("com.ustadmobile.door.ext", "syncableAndPrimary"))
            }

            if(daoFunSpec.hasAnnotation(Update::class.java)) {
                var entityVarName = entityParam.name
                if(entityParam.type.isListOrArray()) {
                    beginControlFlow("${entityParam.name}.forEach")
                    entityVarName = "it"
                }

                add("$entityVarName.${syncableEntityInfo.entityLastChangedByField.name} = _clientId\n")
                beginControlFlow("if(_isSyncablePrimary)")
                add("$entityVarName.${syncableEntityInfo.entityMasterCsnField.name} = 0\n")
                nextControlFlow("else")
                add("$entityVarName.${syncableEntityInfo.entityLocalCsnField.name} = 0\n")
                endControlFlow()

                if(entityParam.type.isListOrArray()) {
                    endControlFlow()
                }
            }



            //Use the SQLite Primary key manager if this is an SQLite insert
            if(daoFunSpec.hasAnnotation(Insert::class.java)) {
                if(entityParam.type.isListOrArray()) {
                    beginControlFlow("${entityParam.name}.forEach")
                    add("it.${syncableEntityInfo.entityLastChangedByField.name} = _clientId\n")
                    add("it.takeIf { it.${syncableEntityInfo.entityPkField.name} == 0L}?." +
                            "${syncableEntityInfo.entityPkField.name} = (_repo as %T).nextId(${syncableEntityInfo.tableId})\n",
                            DoorDatabaseSyncRepository::class)
                    endControlFlow()
                }else {
                    add("${entityParam.name}.${syncableEntityInfo.entityLastChangedByField.name}·=·_clientId\n")
                    add("${entityParam.name}.takeIf·{·it.${syncableEntityInfo.entityPkField.name}·==·0L·}" +
                            "?.${syncableEntityInfo.entityPkField.name}·=·")

                    add("(_repo·as·%T).nextId", DoorDatabaseSyncRepository::class)
                    if(daoFunSpec.isSuspended)
                        add("Async")

                    add("(${syncableEntityInfo.tableId})\n")
                }
            }
        }
    }



    if(daoFunSpec.hasReturnType)
        add("val _result = ")

    add("_dao.${daoFunSpec.name}(")
            .add(daoFunSpec.parameters.joinToString { it.name })
            .add(")\n")

    //if this table has attachments and an update was done, check to delete old data
    if(daoFunSpec.hasAnnotation(Update::class.java)) {
        val entityParam = daoFunSpec.parameters.first()
        val entityComponentType = entityParam.type.unwrapListOrArrayComponentType()
        if(entityComponentType.hasAttachments(processingEnv)) {
            val entityClassName = entityComponentType as ClassName
            val isList = entityParam.type.isListOrArray()

            beginAttachmentStorageFlow(daoFunSpec)

            add("_repo.%M(%L.%M())\n",
                MemberName("com.ustadmobile.door.attachments", "deleteZombieAttachments"),
                if(isList) "it" else entityParam.name,
                MemberName(entityClassName.packageName, "asEntityWithAttachment"))

            endAttachmentStorageFlow(daoFunSpec)
        }

    }

    //Check if a table is modified by this query
    val tableChanged = if(daoFunSpec.hasAnyAnnotation(Update::class.java, Insert::class.java,
            Delete::class.java)) {
        (daoFunSpec.entityParamComponentType as ClassName).simpleName
    }else if(daoFunSpec.isAQueryThatModifiesTables){
        daoFunSpec.getDaoFunEntityModifiedByQuery(allKnownEntityTypesMap)?.simpleName
    }else {
        null
    }

    if(tableChanged != null) {
        add("_repo")
        if(daoFunSpec.isAQueryThatModifiesTables && daoFunSpec.hasReturnType)
            add(".takeIf·{·_result·>·0·}?")
        add(".handleTableChanged(%S)\n", tableChanged)
    }

    if(daoFunSpec.hasReturnType && daoFunSpec.hasAnnotation(Insert::class.java)
            && syncableEntityInfo != null) {
        add("return ")

        if(daoFunSpec.parameters.first().type.isListOrArray()) {
            add("${daoFunSpec.parameters[0].name}.map·{·it.${syncableEntityInfo.entityPkField.name}·}")
            if(daoFunSpec.returnType?.isArrayType() == true)
                add(".%M()", MemberName("kotlin.collections", "toTypedArray"))

            add("\n")
        }else {
            add("${daoFunSpec.parameters[0].name}.${syncableEntityInfo.entityPkField.name}·\n")
        }

    }else if(daoFunSpec.hasReturnType) {
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

    addRepositoryGetSyncableEntitiesCode(boundaryCallbackFunSpec, daoName, processingEnv,
            generateGlobalScopeLaunchBlockForLiveDataTypes = false,
            addReturnDaoResult = false)

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

    addKtorRequestForFunction(daoFunSpec, dbPathVarName = "_dbPath", daoName = daoName,
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
            val hasRepos = (dbTypeEl as TypeElement).dbEnclosedDaos(processingEnv)
                    .any { it.hasAnnotation(Repository::class.java) }

            if(!hasRepos)
                continue //This database has no repositories - skip it

            FileSpec.builder(dbTypeEl.packageName, "${dbTypeEl.simpleName}$SUFFIX_REPOSITORY2")
                    .addDbRepoType(dbTypeEl as TypeElement, processingEnv,
                        syncDaoMode = REPO_SYNCABLE_DAO_CONSTRUCT,
                        addDbVersionProp = true,
                        overrideDataSourceProp = true)
                    .build()
                    .writeToDirsFromArg(OPTION_JVM_DIRS)
            FileSpec.builder(dbTypeEl.packageName, "${dbTypeEl.simpleName}$SUFFIX_REPOSITORY2")
                    .addDbRepoType(dbTypeEl, processingEnv,
                        syncDaoMode = REPO_SYNCABLE_DAO_FROMDB, overrideClearAllTables = false,
                        overrideSyncDao = true, overrideOpenHelper = true,
                        overrideKtorHelpers = true)
                    .build()
                    .writeToDirsFromArg(OPTION_ANDROID_OUTPUT)
            dbTypeEl.allDbEntities(processingEnv).mapNotNull { it as? TypeElement }
                    .filter { it.entityHasAttachments }.forEach { entityEl ->
                        FileSpec.builder(entityEl.packageName, "${entityEl.simpleName}$SUFFIX_ENTITY_WITH_ATTACHMENTS_ADAPTER")
                                .addEntityWithAttachmentAdapterType(entityEl, processingEnv)
                                .addAsEntityWithAttachmentAdapterExtensionFun(entityEl)
                                .build()
                                .writeToDirsFromArg(listOf(OPTION_JVM_DIRS, OPTION_ANDROID_OUTPUT))
            }
        }

        val daos = roundEnv.getElementsAnnotatedWith(Dao::class.java)

        for(daoElement in daos) {
            val daoTypeEl = daoElement as TypeElement
            if(daoTypeEl.isDaoWithRepository) {
                FileSpec.builder(daoElement.packageName,
                        "${daoTypeEl.simpleName}$SUFFIX_REPOSITORY2")
                        .addDaoRepoType(daoTypeEl.asTypeSpecStub(processingEnv),
                            daoTypeEl.asClassName(), processingEnv,
                            allKnownEntityTypesMap = allKnownEntityTypesMap)
                        .build()
                        .writeToDirsFromArg(OPTION_JVM_DIRS)

                FileSpec.builder(daoElement.packageName,
                        "${daoTypeEl.simpleName}$SUFFIX_REPOSITORY2")
                        .addDaoRepoType(daoTypeEl.asTypeSpecStub(processingEnv),
                                daoTypeEl.asClassName(), processingEnv,
                                allKnownEntityTypesMap = allKnownEntityTypesMap,
                                pagingBoundaryCallbackEnabled = true,
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