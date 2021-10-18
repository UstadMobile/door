package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.DoorDatabase
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import com.ustadmobile.door.DoorDatabaseReplicateWrapper
import com.ustadmobile.door.annotation.ReplicateEntity

/**
 * Add a DAO accessor for a database wrapper (e.g. property or function). If the given DAO
 * has queries that modify a syncable entity, the return will be wrapped. If there are no such queries,
 * then the original DAO from the database will be returned by the generated code.
 */
fun TypeSpec.Builder.addWrapperAccessorFunction(daoGetter: ExecutableElement,
                                                processingEnv: ProcessingEnvironment) : TypeSpec.Builder {
    val daoModifiesReplicateEntities = daoGetter.returnType.asTypeElement(processingEnv)
            ?.daoHasRepositoryWriteFunctions(processingEnv) == true

    if(!daoModifiesReplicateEntities) {
        addAccessorOverride(daoGetter, CodeBlock.of("return _db.${daoGetter.accessAsPropertyOrFunctionInvocationCall()}\n"))
    }else {
        val daoType = daoGetter.returnType.asTypeElement(processingEnv)
                ?: throw IllegalStateException("Dao return type is not TypeElement")
        val wrapperClassName = daoType.asClassNameWithSuffix(DoorDatabaseReplicateWrapper.SUFFIX)
        addProperty(PropertySpec.builder("_${daoType.simpleName}",
                daoType.asClassName()).delegate(
                CodeBlock.builder().beginControlFlow("lazy ")
                        .add("%T(_db.${daoGetter.accessAsPropertyOrFunctionInvocationCall()})\n",
                                wrapperClassName)
                        .endControlFlow()
                        .build())
                .build())
        addAccessorOverride(daoGetter, CodeBlock.of("return _${daoType.simpleName}\n"))
    }

    return this
}

/**
 * Add access functions for the KTOR Helper DAOs. The KTOR helper DAOS contain
 * only select queries, so the KTOR Helper DAOs themselves never need a wrapper
 */
fun TypeSpec.Builder.addKtorHelperWrapperAccessorFunction(daoGetter: ExecutableElement,
                                                          processingEnv: ProcessingEnvironment) {
    daoGetter.returnType.asTypeElement(processingEnv)?.daoKtorHelperDaoClassNames?.forEach {
        addFunction(FunSpec.builder("_${it.value.simpleName}")
                .addModifiers(KModifier.OVERRIDE)
                .addCode("return _db._${it.value.simpleName}()\n")
                .build())
    }
}


/**
 * Adds a function that will delegate to the real DAO, or throw an exception if the function is
 * modifying an entity annotated with SyncableEntity
 */
fun TypeSpec.Builder.addDaoFunctionDelegate(daoMethod: ExecutableElement,
        daoTypeEl: TypeElement, processingEnv: ProcessingEnvironment,
        allKnownEntityTypesMap: Map<String, TypeElement>) : TypeSpec.Builder {

    val methodResolved = daoMethod.asMemberOf(daoTypeEl, processingEnv)

    val returnTypeName = methodResolved.suspendedSafeReturnType

    val overridingFunction = overrideAndConvertToKotlinTypes(daoMethod, daoTypeEl.asType() as DeclaredType,
            processingEnv,
            forceNullableReturn = returnTypeName.isNullableAsSelectReturnResult,
            forceNullableParameterTypeArgs = returnTypeName.isNullableParameterTypeAsSelectReturnResult)
            .build()

    addFunction(overridingFunction.toBuilder()
        .addCode(CodeBlock.builder()
                .apply {

                    if(daoMethod.hasAnyAnnotation(Insert::class.java, Update::class.java, Delete::class.java) &&
                        (overridingFunction.entityParamComponentType as? ClassName)?.isReplicateEntity(processingEnv) == true) {
                        add("//must set versionid and/or primary key here\n")
                    }

                    if(overridingFunction.returnType != null && overridingFunction.returnType != UNIT) {
                        add("return ")
                    }
                }
                .addDelegateFunctionCall("_dao", overridingFunction)
                .build())
        .build())

    return this
}

/**
 * Add a TypeSpec representing a database wrapper for the given database to the filespec
 */
fun FileSpec.Builder.addDbWrapperTypeSpec(dbTypeEl: TypeElement,
                                         processingEnv: ProcessingEnvironment,
                                         allKnownEntityTypesMap: Map<String, TypeElement>,
                                         overrideKtorHelperDaos: Boolean = false,
                                         overrideSyncDao: Boolean = false,
                                         addJdbcOverrides: Boolean = true,
                                         addRoomOverrides: Boolean = false): FileSpec.Builder {
    val dbClassName = dbTypeEl.asClassName()
    addType(
            TypeSpec.classBuilder("${dbTypeEl.simpleName}${DoorDatabaseReplicateWrapper.SUFFIX}")
                    .addAnnotation(AnnotationSpec.builder(Suppress::class)
                            .addMember("%S, %S", "REDUNDANT_PROJECTION", "ClassName")
                            .build())
                    .superclass(dbClassName)
                    .addSuperinterface(DoorDatabaseReplicateWrapper::class.asClassName())
                    .primaryConstructor(FunSpec.constructorBuilder()
                            .addParameter("_db", dbClassName)
                            .build())
                    .addProperty(PropertySpec.builder("_db", dbClassName, KModifier.PRIVATE)
                            .initializer("_db").build())
                    .applyIf(addJdbcOverrides) {
                        addDbVersionProperty(dbTypeEl)
                        addFunction(FunSpec.builder("createAllTables")
                                .addModifiers(KModifier.OVERRIDE)
                                .returns(List::class.parameterizedBy(String::class))
                                .addCode("return _db.createAllTables()\n")
                                .build())
                        addDataSourceProperty("_db")
                        addFunction(FunSpec.builder("wrapForNewTransaction")
                            .addOverrideWrapNewTransactionFun()
                            .addCode("return transactionDb.%M(dbKClass) as T\n",
                                MemberName("com.ustadmobile.door", "wrap"))
                            .build())
                        addProperty(PropertySpec.builder("dbName", String::class, KModifier.OVERRIDE)
                            .getter(FunSpec.getterBuilder()
                                .addCode("return \"DoorWrapper for [\${_db.toString()}]\"\n")
                                .build())
                            .build())
                    }
                    .apply {
                        dbTypeEl.allDbClassDaoGetters(processingEnv).forEach {daoGetter ->
                            addWrapperAccessorFunction(daoGetter, processingEnv)

                            if(overrideKtorHelperDaos &&
                                    daoGetter.returnType.asTypeElement(processingEnv)?.isDaoThatRequiresKtorHelper == true) {
                                addKtorHelperWrapperAccessorFunction(daoGetter, processingEnv)
                            }
                        }
                    }
                    .addProperty(PropertySpec.builder("realDatabase", DoorDatabase::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .getter(FunSpec.getterBuilder().addCode("return _db\n")
                                    .build())
                            .build())
                    .addFunction(FunSpec.builder("clearAllTables")
                            .addModifiers(KModifier.OVERRIDE)
                            .addCode("_db.clearAllTables()\n")
                            .build())
                    .addFunction(FunSpec.builder("runInTransaction")
                            .addModifiers(KModifier.OVERRIDE)
                            .addParameter("body", ClassName("kotlinx.coroutines",
                                    "Runnable"))
                            .addCode("_db.runInTransaction(body)\n")
                            .build())
                    .applyIf(dbTypeEl.isDbSyncable(processingEnv)) {
                        addProperty(PropertySpec.builder("master", Boolean::class)
                                .addModifiers(KModifier.OVERRIDE)
                                .getter(FunSpec.getterBuilder()
                                        .addCode("return _db.master\n")
                                        .build())
                                .build())
                    }
                    .applyIf(overrideSyncDao && dbTypeEl.isDbSyncable(processingEnv)) {
                        addFunction(FunSpec.builder("_syncDao")
                                .addModifiers(KModifier.OVERRIDE)
                                .addCode("return _db._syncDao()\n")
                                .build())
                        addFunction(FunSpec.builder("_syncHelperEntitiesDao")
                                .addModifiers(KModifier.OVERRIDE)
                                .addCode("return _db._syncHelperEntitiesDao()\n")
                                .build())
                    }
                    .applyIf(addRoomOverrides) {
                        addRoomDatabaseCreateOpenHelperFunction()
                        addRoomCreateInvalidationTrackerFunction()
                        addOverrideGetRoomInvalidationTracker("_db")
                    }
                    .build())
            .build()

    return this
}

/**
 * Add a TypeSpec to the FileSpec that is a wrapper class for the given DAO
 */
fun FileSpec.Builder.addDaoWrapperTypeSpec(daoTypeElement: TypeElement,
                                            processingEnv: ProcessingEnvironment,
                                            allKnownEntityTypesMap: Map<String, TypeElement>): FileSpec.Builder {
    addType(TypeSpec.classBuilder(daoTypeElement.asClassNameWithSuffix(DoorDatabaseReplicateWrapper.SUFFIX))
            .superclass(daoTypeElement.asClassName())
            .primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter("_dao", daoTypeElement.asClassName())
                    .build())
            .addProperty(PropertySpec.builder("_dao", daoTypeElement.asClassName(),
                    KModifier.PRIVATE)
                    .initializer("_dao")
                    .build())
            .apply {
                daoTypeElement.allOverridableMethods(processingEnv).forEach {daoMethodEl ->
                    addDaoFunctionDelegate(daoMethodEl, daoTypeElement, processingEnv,
                            allKnownEntityTypesMap)
                }
            }
            .build())

    return this
}


/**
 * Determine if this TypeElement (or any of its ancestors) representing a DAO has any functions with the Insert,
 * Delete, or Update annotation that are acting on a ReplicateEntity
 */
private fun TypeElement.daoHasRepositoryWriteFunctions(
    processingEnv: ProcessingEnvironment
) : Boolean {
    return ancestorsAsList(processingEnv).any {
        it.allDaoClassModifyingQueryMethods(checkQueryAnnotation = false).any { daoMethodEl ->
            val paramType = daoMethodEl.parameters.first().asType().unwrapListOrArrayComponentType()
            paramType.asTypeElement(processingEnv)?.hasAnnotation(ReplicateEntity::class.java) == true
        }
    }
}

/**
 * Generates an implementation of DoorDatabaseReplicateWrapper for databases and daos being processed.
 */
class DbProcessorReplicateWrapper: AbstractDbProcessor()  {

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        roundEnv.getElementsAnnotatedWith(Database::class.java).map { it as TypeElement }.forEach {dbTypeEl ->
            //jvm version
            if(dbTypeEl.dbHasReadOnlyWrapper(processingEnv)) {
                FileSpec.builder(dbTypeEl.qualifiedPackageName(processingEnv),
                        "${dbTypeEl.simpleName}${DoorDatabaseReplicateWrapper.SUFFIX}")
                        .addDbWrapperTypeSpec(dbTypeEl, processingEnv, allKnownEntityTypesMap)
                        .build()
                        .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_JVM_DIRS)

                FileSpec.builder(dbTypeEl.packageName,
                        "${dbTypeEl.simpleName}${DoorDatabaseReplicateWrapper.SUFFIX}")
                        .addDbWrapperTypeSpec(dbTypeEl, processingEnv, allKnownEntityTypesMap,
                            overrideKtorHelperDaos = true,
                            addJdbcOverrides = false,
                            overrideSyncDao = true,
                            addRoomOverrides = true)
                        .build()
                        .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_ANDROID_OUTPUT)
            }
        }

        roundEnv.getElementsAnnotatedWith(Dao::class.java).map { it as TypeElement }.forEach {daoTypeEl ->
            if(daoTypeEl.daoHasRepositoryWriteFunctions(processingEnv)) {
                FileSpec.builder(daoTypeEl.packageName,
                        "${daoTypeEl.simpleName}${DoorDatabaseReplicateWrapper.SUFFIX}")
                        .addDaoWrapperTypeSpec(daoTypeEl, processingEnv, allKnownEntityTypesMap)
                        .build().apply {
                            writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_JVM_DIRS)
                            writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_ANDROID_OUTPUT)
                        }

            }
        }

        return true
    }

}