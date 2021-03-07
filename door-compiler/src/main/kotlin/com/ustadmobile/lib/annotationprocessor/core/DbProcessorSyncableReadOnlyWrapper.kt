package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Database
import androidx.room.Dao
import com.squareup.kotlinpoet.*
import com.ustadmobile.door.DoorDatabase
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import com.ustadmobile.door.DoorDatabaseSyncableReadOnlyWrapper

/**
 * Add a DAO accessor for a database wrapper (e.g. property or function). If the given DAO
 * has queries that modify a syncable entity, the return will be wrapped. If there are no such queries,
 * then the original DAO from the database will be returned by the generated code.
 */
fun TypeSpec.Builder.addWrapperAccessorFunction(daoGetter: ExecutableElement,
                                                processingEnv: ProcessingEnvironment,
                                                allKnownEntityTypesMap: Map<String, TypeElement>) : TypeSpec.Builder {
    val daoModifiesSyncableEntities = daoGetter.returnType.asTypeElement(processingEnv)
            ?.daoHasSyncableWriteMethods(processingEnv, allKnownEntityTypesMap) == true

    if(!daoModifiesSyncableEntities) {
        addAccessorOverride(daoGetter, CodeBlock.of("return _db.${daoGetter.accessAsPropertyOrFunctionInvocationCall()}\n"))
    }else {
        val daoType = daoGetter.returnType.asTypeElement(processingEnv)
                ?: throw IllegalStateException("Dao return type is not TypeElement")
        val wrapperClassName = daoType.asClassNameWithSuffix(DoorDatabaseSyncableReadOnlyWrapper.SUFFIX)
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

    if(daoMethod.isDaoMethodModifyingSyncableEntity(daoTypeEl, processingEnv, allKnownEntityTypesMap)) {
        addFunction(overridingFunction.toBuilder()
                .addCode("throw %T(%S)\n", IllegalStateException::class,
                        "Cannot use DB to modify syncable entity")
                .build())
    }else {
        addFunction(overridingFunction.toBuilder()
                .addCode(CodeBlock.builder()
                        .apply {
                            if(overridingFunction.returnType != null && overridingFunction.returnType != UNIT) {
                                add("return ")
                            }
                        }
                        .addDelegateFunctionCall("_dao", overridingFunction)
                        .build())
                .build())
    }

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
            TypeSpec.classBuilder("${dbTypeEl.simpleName}${DoorDatabaseSyncableReadOnlyWrapper.SUFFIX}")
                    .addAnnotation(AnnotationSpec.builder(Suppress::class)
                            .addMember("%S, %S", "REDUNDANT_PROJECTION", "ClassName")
                            .build())
                    .superclass(dbClassName)
                    .addSuperinterface(DoorDatabaseSyncableReadOnlyWrapper::class.asClassName())
                    .primaryConstructor(FunSpec.constructorBuilder()
                            .addParameter("_db", dbClassName)
                            .build())
                    .addProperty(PropertySpec.builder("_db", dbClassName, KModifier.PRIVATE)
                            .initializer("_db").build())
                    .applyIf(addJdbcOverrides) {
                        addInitializerBlock(CodeBlock.builder()
                                .add("sourceDatabase = _db\n")
                                .build())
                        addDbVersionProperty(dbTypeEl)
                        addFunction(FunSpec.builder("createAllTables")
                                .addModifiers(KModifier.OVERRIDE)
                                .addCode("_db.createAllTables()\n")
                                .build())
                    }
                    .apply {
                        dbTypeEl.allDbClassDaoGetters(processingEnv).forEach {daoGetter ->
                            addWrapperAccessorFunction(daoGetter, processingEnv, allKnownEntityTypesMap)

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
    addType(TypeSpec.classBuilder(daoTypeElement.asClassNameWithSuffix(DoorDatabaseSyncableReadOnlyWrapper.SUFFIX))
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
 * Determine if this TypeElement (or any of it's ancestors) representing a DAO has any methods that
 * modify a syncable entity
 */
private fun TypeElement.daoHasSyncableWriteMethods(
        processingEnv: ProcessingEnvironment, allKnownEntityTypesMap: Map<String, TypeElement>): Boolean {

    return ancestorsAsList(processingEnv).any {
        it.allDaoClassModifyingQueryMethods().any { daoMethodEl ->
            daoMethodEl.isDaoMethodModifyingSyncableEntity(this, processingEnv, allKnownEntityTypesMap)
        }
    }
}

/**
 * Generates the DbWrapper class to prevent accidental usage of insert, update, and delete functions
 * for syncable entities on the database which must be routed via the repository for sync to work.
 */
class DbProcessorSyncableReadOnlyWrapper: AbstractDbProcessor()  {

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        roundEnv.getElementsAnnotatedWith(Database::class.java).map { it as TypeElement }.forEach {dbTypeEl ->
            //jvm version
            if(dbTypeEl.isDbSyncable(processingEnv)) {
                FileSpec.builder(dbTypeEl.qualifiedPackageName(processingEnv),
                        "${dbTypeEl.simpleName}${DoorDatabaseSyncableReadOnlyWrapper.SUFFIX}")
                        .addDbWrapperTypeSpec(dbTypeEl, processingEnv, allKnownEntityTypesMap)
                        .build()
                        .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_JVM_DIRS)

                FileSpec.builder(dbTypeEl.packageName,
                        "${dbTypeEl.simpleName}${DoorDatabaseSyncableReadOnlyWrapper.SUFFIX}")
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
            if(daoTypeEl.daoHasSyncableWriteMethods(processingEnv, allKnownEntityTypesMap)) {
                FileSpec.builder(daoTypeEl.packageName,
                        "${daoTypeEl.simpleName}${DoorDatabaseSyncableReadOnlyWrapper.SUFFIX}")
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