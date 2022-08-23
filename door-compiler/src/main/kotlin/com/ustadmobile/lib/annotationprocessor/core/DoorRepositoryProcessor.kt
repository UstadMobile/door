package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.*
import com.ustadmobile.door.room.*
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import com.ustadmobile.door.*
import com.ustadmobile.door.annotation.RepoHttpAccessible
import com.ustadmobile.door.annotation.Repository
import com.ustadmobile.door.attachments.EntityWithAttachment
import com.ustadmobile.door.replication.ReplicationSubscriptionManager
import com.ustadmobile.lib.annotationprocessor.core.AbstractDbProcessor.Companion.CLASSNAME_ILLEGALSTATEEXCEPTION
import com.ustadmobile.lib.annotationprocessor.core.DoorRepositoryProcessor.Companion.BOUNDARY_CALLBACK_CLASSNAME
import com.ustadmobile.lib.annotationprocessor.core.DoorRepositoryProcessor.Companion.DATASOURCEFACTORY_TO_BOUNDARYCALLBACK_VARNAME
import com.ustadmobile.lib.annotationprocessor.core.DoorRepositoryProcessor.Companion.SUFFIX_ENTITY_WITH_ATTACHMENTS_ADAPTER
import com.ustadmobile.lib.annotationprocessor.core.DoorRepositoryProcessor.Companion.SUFFIX_REPOSITORY2
import com.ustadmobile.lib.annotationprocessor.core.ext.*
import io.ktor.client.*

/**
 * Generate the table id map of entity names (strings) to the table id as per the syncableentity
 * annotation
 */
fun TypeSpec.Builder.addTableIdMapProperty() : TypeSpec.Builder {
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
 * database as per the dbKSClassDeclaration parameter.
 */
fun FileSpec.Builder.addDbRepoType(
    dbKSClassDeclaration: KSClassDeclaration,
    target: DoorTarget
): FileSpec.Builder {
    addType(TypeSpec.classBuilder(dbKSClassDeclaration.toClassNameWithSuffix(SUFFIX_REPOSITORY2))
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
        .addProperty(PropertySpec.builder("_db", dbKSClassDeclaration.toClassName())
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
            .initializer("%T()", RepositoryHelper::class)
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
                .addModifiers(KModifier.OVERRIDE)
                .addCode("throw %T(%S)\n",
                    ClassName("kotlin", "IllegalStateException"),
                    "Cannot use a repository to createAllTables!")
                .build())
            addOverrideGetInvalidationTracker("_db")
            addDbVersionProperty(dbKSClassDeclaration)
        }
        .applyIf(target == DoorTarget.ANDROID) {
            addRoomCreateInvalidationTrackerFunction()
            addOverrideGetRoomInvalidationTracker("_db")
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


/**
 * Generate an EntityWithAttachment inline adapter class
 */
fun FileSpec.Builder.addEntityWithAttachmentAdapterType(
    entityKSClass: KSClassDeclaration,
) : FileSpec.Builder {
    val attachmentInfo = EntityAttachmentInfo(entityKSClass)
    val nullableStringClassName = String::class.asClassName().copy(nullable = true)
    addType(TypeSpec.classBuilder(entityKSClass.toClassNameWithSuffix(SUFFIX_ENTITY_WITH_ATTACHMENTS_ADAPTER))
        .addModifiers(KModifier.INLINE)
        .addSuperinterface(EntityWithAttachment::class)
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("entity", entityKSClass.toClassName())
                .build())
        .addProperty(
            PropertySpec.builder("entity", entityKSClass.toClassName())
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
                    .addCode("return %S\n", entityKSClass.simpleName.asString())
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
fun FileSpec.Builder.addAsEntityWithAttachmentAdapterExtensionFun(
    entityWithAttachment: KSClassDeclaration
): FileSpec.Builder {
    addFunction(FunSpec.builder("asEntityWithAttachment")
        .addModifiers(KModifier.INLINE)
        .receiver(entityWithAttachment.toClassName())
        .returns(EntityWithAttachment::class)
        .addCode("return %T(this)\n",
            entityWithAttachment.toClassNameWithSuffix(SUFFIX_ENTITY_WITH_ATTACHMENTS_ADAPTER))
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
        .superclass(daoClassName)
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
            daoKSClass.getAllDaoFunctionsIncSuperTypesToGenerate().forEach { daoFun ->
                //If this is OK, then remove the name param - no need for that...
                addDaoRepoFun(daoFun, daoKSClass, daoKSClass.simpleName.asString(), target, environment, resolver)
            }
        }
        .build())

    return this
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

    add("_dao.${daoFun.simpleName.asString()}(")
            .add(daoFun.parameters.joinToString { it.name?.asString() ?: "" })
            .add(")\n")



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
        val dbSymbols = resolver.getSymbolsWithAnnotation("androidx.room.Database")
            .filterIsInstance<KSClassDeclaration>()
            .filter { dbKSClassDecl ->
                dbKSClassDecl.dbEnclosedDaos().any { it.hasAnnotation(Repository::class) }
            }

        val entitiesWithAttachments = resolver.getSymbolsWithAnnotation("com.ustadmobile.door.annotation.AttachmentUri")
            .filterIsInstance<KSPropertyDeclaration>()
            .mapNotNull {
                it.parentDeclaration as? KSClassDeclaration
            }
        val daoSymbols = resolver.getSymbolsWithAnnotation("androidx.room.Dao")
            .filterIsInstance<KSClassDeclaration>()

        val target = environment.doorTarget(resolver)

        dbSymbols.forEach { dbKSClass ->
            FileSpec.builder(dbKSClass.packageName.asString(),
                "${dbKSClass.simpleName.asString()}$SUFFIX_REPOSITORY2")
                .addDbRepoType(dbKSClass, target)
                .build()
                .writeTo(environment.codeGenerator, false)
        }

        entitiesWithAttachments.forEach { entityWithAttachment ->
            FileSpec.builder(entityWithAttachment.packageName.asString(),
                "${entityWithAttachment.simpleName.asString()}$SUFFIX_ENTITY_WITH_ATTACHMENTS_ADAPTER")
                .addEntityWithAttachmentAdapterType(entityWithAttachment)
                .addAsEntityWithAttachmentAdapterExtensionFun(entityWithAttachment)
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

        const val SUFFIX_ENTITY_WITH_ATTACHMENTS_ADAPTER = "_EwaAdapter"

        /**
         * A static string which is generated for the database name part of the http path, which is
         * passed from the database repository to the DAO repository so it can use the correct http
         * path e.g. endpoint/dbname/daoname
         */
        const val DB_NAME_VAR = "_DB_NAME"

        const val DATASOURCEFACTORY_TO_BOUNDARYCALLBACK_VARNAME = "_dataSourceFactoryToBoundaryCallbackMap"

        val BOUNDARY_CALLBACK_CLASSNAME = ClassName("com.ustadmobile.door",
            "RepositoryBoundaryCallback")
    }
}