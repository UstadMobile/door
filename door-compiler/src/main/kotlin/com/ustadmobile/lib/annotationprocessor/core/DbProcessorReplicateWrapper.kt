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
import com.ustadmobile.door.annotation.LastChangedTime
import com.ustadmobile.door.annotation.ReplicateEntity
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind

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
                        .add("%T(_db, _db.${daoGetter.accessAsPropertyOrFunctionInvocationCall()})\n",
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
fun TypeSpec.Builder.addKtorHelperWrapperAccessorFunction(
    daoGetter: ExecutableElement,
    processingEnv: ProcessingEnvironment
) {
    daoGetter.returnType.asTypeElement(processingEnv)?.daoKtorHelperDaoClassNames?.forEach {
        addFunction(FunSpec.builder("_${it.value.simpleName}")
                .addModifiers(KModifier.OVERRIDE)
                .addCode("return _db._${it.value.simpleName}()\n")
                .build())
    }
}


fun CodeBlock.Builder.beginAttachmentStorageFlow(daoFunSpec: FunSpec) {
    val entityParam = daoFunSpec.parameters.first()
    val isList = entityParam.type.isListOrArray()

    if(!daoFunSpec.isSuspended)
        beginRunBlockingControlFlow()

    if(isList)
        beginControlFlow("${entityParam.name}.forEach")
}

fun CodeBlock.Builder.endAttachmentStorageFlow(daoFunSpec: FunSpec) {
    val entityParam = daoFunSpec.parameters.first()
    val isList = entityParam.type.isListOrArray()

    if(!daoFunSpec.isSuspended)
        endControlFlow()

    if(isList)
        endControlFlow()
}


/**
 * Adds a function that will delegate to the real DAO, or throw an exception if the function is
 * modifying an entity annotated with SyncableEntity
 */
fun TypeSpec.Builder.addDaoFunctionDelegate(
    daoMethod: ExecutableElement,
    daoTypeEl: TypeElement,
    processingEnv: ProcessingEnvironment,
    doorTarget: DoorTarget,
) : TypeSpec.Builder {

    val methodResolved = daoMethod.asMemberOf(daoTypeEl, processingEnv)

    val returnTypeName = methodResolved.suspendedSafeReturnType

    val overridingFunction = overrideAndConvertToKotlinTypes(daoMethod, daoTypeEl.asType() as DeclaredType,
            processingEnv,
            forceNullableReturn = returnTypeName.isNullableAsSelectReturnResult,
            forceNullableParameterTypeArgs = returnTypeName.isNullableParameterTypeAsSelectReturnResult)
            .build()

    var setPk = false
    lateinit var entityTypeEl : TypeElement
    var pkField : Element? = null

    //If running on JS, non-suspended (sync) version is NOT allowed
    val isSyncFunctionOnJs = doorTarget == DoorTarget.JS && !overridingFunction.isSuspended
            && (overridingFunction.returnType?.isDataSourceFactoryOrLiveData() == false)

    val entityParam = overridingFunction.parameters.firstOrNull()
    val entityComponentType = entityParam?.type?.unwrapListOrArrayComponentType()

    addFunction(overridingFunction.toBuilder()
        .addCode(CodeBlock.builder()
                .applyIf(isSyncFunctionOnJs) {
                    if(doorTarget == DoorTarget.JS && !overridingFunction.isSuspended) {
                        add("throw %T(%S)\n", ClassName("kotlin", "IllegalStateException"),
                            "Synchronous db access is NOT possible on Javascript!")
                    }
                }
                .applyIf(!isSyncFunctionOnJs) {
                    if(daoMethod.hasAnyAnnotation(Insert::class.java, Update::class.java, Delete::class.java) &&
                        (overridingFunction.entityParamComponentType as? ClassName)?.isReplicateEntity(processingEnv) == true) {


                        if(daoMethod.hasAnyAnnotation(Update::class.java, Insert::class.java)
                            && entityComponentType?.hasAttachments(processingEnv) == true) {
                            val isList = entityParam.type.isListOrArray()

                            beginAttachmentStorageFlow(overridingFunction)

                            val entityClassName = entityComponentType as ClassName

                            add("_db.%M(%L.%M())\n",
                                MemberName("com.ustadmobile.door.attachments", "storeAttachment"),
                                if(isList) "it" else entityParam.name,
                                MemberName(entityClassName.packageName, "asEntityWithAttachment"))

                            endAttachmentStorageFlow(overridingFunction)
                        }



                        entityTypeEl =  overridingFunction.entityParamComponentType
                            .asComponentClassNameIfList().asTypeElement(processingEnv)
                            ?: throw IllegalStateException("addDaoFunctionDelegate cannot get entity type spec")
                        pkField = entityTypeEl.entityPrimaryKey

                        val tableId = entityTypeEl.getAnnotation(ReplicateEntity::class.java).tableId

                        setPk = daoMethod.hasAnyAnnotation(Insert::class.java) && entityTypeEl.isReplicateEntityWithAutoIncPrimaryKey

                        val setLastChangedField = entityTypeEl.enclosedElementsWithAnnotation(LastChangedTime::class.java,
                            ElementKind.FIELD)

                        if(setPk || setLastChangedField.isNotEmpty()) {
                            if(setPk) {
                                add("val _pkManager = _db.%M.%M\n",
                                    MemberName("com.ustadmobile.door.ext", "rootDatabase"),
                                    MemberName("com.ustadmobile.door.ext", "doorPrimaryKeyManager"))
                            }

                            var varName = overridingFunction.parameters.first().name

                            val isListParam = overridingFunction.parameters.first().type.isListOrArray()

                            if(isListParam) {
                                varName = "it"
                                add("val _generatedPks = mutableListOf<Long>()\n")
                                beginControlFlow("${overridingFunction.parameters.first().name}.iterator().forEach ")
                            }

                            if(setPk) {
                                beginControlFlow("if($varName.${pkField?.simpleName} == 0L)")
                                add("val _newPk = _pkManager.nextId")
                                if(overridingFunction.isSuspended)
                                    add("Async")
                                add("($tableId)\n")
                                add("$varName.${pkField?.simpleName} = _newPk\n")
                                if(isListParam)
                                    add("_generatedPks += _newPk\n")
                                endControlFlow()
                            }

                            if(setLastChangedField.isNotEmpty()) {
                                add("$varName.${setLastChangedField.first().simpleName} = %M()\n",
                                    MemberName("com.ustadmobile.door.util", "systemTimeInMillis"))
                            }

                            if(overridingFunction.parameters.first().type.isListOrArray()) {
                                endControlFlow()
                            }
                        }

                        add("//must set versionid and/or primary key here\n")
                    }

                    if(!setPk && overridingFunction.returnType != null && overridingFunction.returnType != UNIT) {
                        add("return ")
                    }
                }
                .applyIf(!isSyncFunctionOnJs) {
                    addDelegateFunctionCall("_dao", overridingFunction)
                    .add("\n")
                    .applyIf(daoMethod.hasAnnotation(Update::class.java) &&
                                entityComponentType?.hasAttachments(processingEnv) == true) {
                        //if this table has attachments and an update was done, check to delete old data

                        val entityClassName = entityComponentType as ClassName
                        val isList = entityParam.type.isListOrArray()

                        beginAttachmentStorageFlow(overridingFunction)

                        add("_db.%M(%L.%M())\n",
                            MemberName("com.ustadmobile.door.attachments", "deleteZombieAttachments"),
                            if(isList) "it" else entityParam.name,
                            MemberName(entityClassName.packageName, "asEntityWithAttachment"))

                        endAttachmentStorageFlow(overridingFunction)
                    }
                    .applyIf(setPk && overridingFunction.returnType != null && overridingFunction.returnType != UNIT) {
                        //We need to override this to return the PKs that were really generated
                        val entityParamType = overridingFunction.parameters.first().type
                        if(entityParamType.isListOrArray()) {
                            add("return _generatedPks")
                            if(entityParamType.isArrayType())
                                add("toTypedArray()")
                            add("\n")
                        }else {
                            add("return ${overridingFunction.parameters.first().name}.${pkField?.simpleName}\n")
                        }
                    }
                }
                .build())
        .build())

    return this
}

/**
 * Add a TypeSpec representing a database wrapper for the given database to the filespec
 */
fun FileSpec.Builder.addDbWrapperTypeSpec(
    dbTypeEl: TypeElement,
    processingEnv: ProcessingEnvironment,
    target: DoorTarget,
): FileSpec.Builder {
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
                    .applyIf(target == DoorTarget.JS || target == DoorTarget.JVM) {
                        addDbVersionProperty(dbTypeEl)
                        addFunction(FunSpec.builder("createAllTables")
                                .addModifiers(KModifier.OVERRIDE)
                                .returns(List::class.parameterizedBy(String::class))
                                .addCode("return _db.createAllTables()\n")
                                .build())
                    }
                    .applyIf(target == DoorTarget.JVM) {
                        addFunction(FunSpec.builder("wrapForNewTransaction")
                            .addOverrideWrapNewTransactionFun()
                            .addCode("return transactionDb.%M(dbKClass) as T\n",
                                MemberName("com.ustadmobile.door.ext", "wrap"))
                            .build())
                    }
                    .addProperty(PropertySpec.builder("dbName", String::class, KModifier.OVERRIDE)
                        .getter(FunSpec.getterBuilder()
                            .addCode("return \"DoorWrapper for [\${_db.toString()}]\"\n")
                            .build())
                        .build())
                    .apply {
                        dbTypeEl.allDbClassDaoGetters(processingEnv).forEach {daoGetter ->
                            addWrapperAccessorFunction(daoGetter, processingEnv)
                        }
                    }
                    .addProperty(PropertySpec.builder("realDatabase", DoorDatabase::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .getter(FunSpec.getterBuilder().addCode("return _db\n")
                                    .build())
                            .build())
                    .applyIf(target == DoorTarget.JS) {
                        addFunction(FunSpec.builder("clearAllTablesAsync")
                            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                            .addCode("_db.clearAllTablesAsync()\n")
                            .build())
                    }
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
                    .applyIf(target == DoorTarget.ANDROID) {
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
fun FileSpec.Builder.addDaoWrapperTypeSpec(
    daoTypeElement: TypeElement,
    processingEnv: ProcessingEnvironment,
    target: DoorTarget
): FileSpec.Builder {
    addType(TypeSpec.classBuilder(daoTypeElement.asClassNameWithSuffix(DoorDatabaseReplicateWrapper.SUFFIX))
            .superclass(daoTypeElement.asClassName())
            .primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter("_db", DoorDatabase::class)
                    .addParameter("_dao", daoTypeElement.asClassName())
                    .build())
            .addProperty(PropertySpec.builder("_db", DoorDatabase::class,
                KModifier.PRIVATE)
                .initializer("_db")
                .build())
            .addProperty(PropertySpec.builder("_dao", daoTypeElement.asClassName(),
                    KModifier.PRIVATE)
                    .initializer("_dao")
                    .build())
            .apply {
                daoTypeElement.allOverridableMethods(processingEnv).forEach {daoMethodEl ->
                    addDaoFunctionDelegate(daoMethodEl, daoTypeElement, processingEnv, target)
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
    return allOverridableMethods(processingEnv).any { daoMethodEl ->
        val fnResolved = daoMethodEl.asMemberOf(this, processingEnv)
        val paramType = fnResolved.parameterTypes.firstOrNull()?.unwrapListOrArrayComponentType(processingEnv)
        paramType?.asTypeElement(processingEnv)?.hasAnnotation(ReplicateEntity::class.java) == true
    }
}

/**
 * Generates an implementation of DoorDatabaseReplicateWrapper for databases and daos being processed.
 */
class DbProcessorReplicateWrapper: AbstractDbProcessor()  {

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val targetMap = mapOf(DoorTarget.JVM to AnnotationProcessorWrapper.OPTION_JVM_DIRS,
            DoorTarget.JS to AnnotationProcessorWrapper.OPTION_JS_OUTPUT,
            DoorTarget.ANDROID to AnnotationProcessorWrapper.OPTION_ANDROID_OUTPUT)

        roundEnv.getElementsAnnotatedWith(Database::class.java).map { it as TypeElement }.forEach {dbTypeEl ->
            //jvm version
            if(dbTypeEl.dbHasReplicateWrapper(processingEnv)) {
                targetMap.forEach { target ->
                    FileSpec.builder(dbTypeEl.qualifiedPackageName(processingEnv),
                        "${dbTypeEl.simpleName}${DoorDatabaseReplicateWrapper.SUFFIX}")
                        .addDbWrapperTypeSpec(dbTypeEl, processingEnv, target.key)
                        .build()
                        .writeToDirsFromArg(target.value)
                }
            }
        }



        roundEnv.getElementsAnnotatedWith(Dao::class.java).map { it as TypeElement }.forEach {daoTypeEl ->
            if(daoTypeEl.daoHasRepositoryWriteFunctions(processingEnv)) {
                targetMap.forEach { target ->
                    FileSpec.builder(daoTypeEl.packageName,
                        "${daoTypeEl.simpleName}${DoorDatabaseReplicateWrapper.SUFFIX}")
                        .addDaoWrapperTypeSpec(daoTypeEl, processingEnv, target.key)
                        .build()
                        .writeToDirsFromArg(target.value)
                }
            }
        }

        return true
    }

}