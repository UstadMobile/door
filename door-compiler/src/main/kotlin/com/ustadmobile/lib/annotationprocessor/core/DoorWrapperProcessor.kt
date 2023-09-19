package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import com.ustadmobile.door.DoorDatabaseWrapper
import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.door.annotation.ReplicateLastModified
import com.ustadmobile.door.message.DoorMessageCallback
import com.ustadmobile.door.nodeevent.NodeEventManagerCommon
import com.ustadmobile.door.nodeevent.NodeEventManagerJvm
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.lib.annotationprocessor.core.ext.*
import kotlin.reflect.KClass

/**
 * Add a DAO accessor for a database replication wrapper (e.g. property or function). If the given DAO
 * has queries that modify an entity with @ReplicateEntity annotation, the return will be wrapped. If there are no
 * such queries, then the original DAO from the database will be returned by the generated code.
 */
fun TypeSpec.Builder.addDbDaoWrapperPropOrGetter(
    daoPropOrGetterDecl: KSDeclaration,
    resolver: Resolver,
): TypeSpec.Builder {
    val daoClassDeclaration = daoPropOrGetterDecl.propertyOrReturnType()
        ?.resolve()?.declaration as? KSClassDeclaration
    if(daoClassDeclaration?.daoHasReplicateEntityWriteFunctions(resolver) != true) {
        addDaoPropOrGetterDelegate(daoPropOrGetterDecl, "_db.")
    }else {
        val wrapperClassName = daoClassDeclaration.toClassNameWithSuffix(DoorDatabaseWrapper.SUFFIX)

        addProperty(PropertySpec.builder("_${daoClassDeclaration.simpleName.asString()}",
            daoClassDeclaration.toClassName()).delegate(
            CodeBlock.builder().beginControlFlow("lazy ")
                .add("%T(_db, _db.${daoPropOrGetterDecl.toPropertyOrEmptyFunctionCaller()})\n",
                    wrapperClassName)
                .endControlFlow()
                .build())
            .build())
        addDaoPropOrGetterOverride(daoPropOrGetterDecl,
            CodeBlock.of("return _${daoClassDeclaration.simpleName.asString()}\n"))
    }

    return this
}

/**
 * Adds a function that will delegate to the real DAO, or throw an exception if the function is
 * modifying an entity annotated with SyncableEntity
 */
fun TypeSpec.Builder.addDaoFunctionDelegate(
    daoFunDeclaration: KSFunctionDeclaration,
    daoClassDeclaration: KSClassDeclaration,
    resolver: Resolver,
    doorTarget: DoorTarget,
    logger: KSPLogger,
) : TypeSpec.Builder {
    val functionResolved = daoFunDeclaration.asMemberOf(daoClassDeclaration.asType(emptyList()))

    val overridingFunction = daoFunDeclaration.toFunSpecBuilder(resolver,
            daoClassDeclaration.asType(emptyList()), logger)
        .removeAbstractModifier()
        .addModifiers(KModifier.OVERRIDE)
        .build()

    var setPk = false
    var pkProp : KSPropertyDeclaration? = null

    //If running on JS, non-suspended (sync) version is NOT allowed
    val isSyncFunctionOnJs = doorTarget == DoorTarget.JS && !overridingFunction.isSuspended
            && (overridingFunction.returnType.isAsynchronousReturnType() == false)

    val entityComponentClassDecl: KSClassDeclaration? =
        if(daoFunDeclaration.isDaoReplicateEntityWriteFunction(resolver, daoClassDeclaration)) {
            functionResolved.firstParamEntityType(resolver).declaration as KSClassDeclaration
        }else {
            null
        }

    addFunction(overridingFunction.toBuilder()
        .addCode(CodeBlock.builder()
            .applyIf(isSyncFunctionOnJs) {
                if(doorTarget == DoorTarget.JS && !overridingFunction.isSuspended) {
                    add("throw %T(%S)\n", ClassName("kotlin", "IllegalStateException"),
                        "${daoFunDeclaration.simpleName.asString()}: synchronous db access is NOT possible on Javascript!")
                }
            }
            .applyIf(!isSyncFunctionOnJs) {
                if(daoFunDeclaration.isDaoReplicateEntityWriteFunction(resolver, daoClassDeclaration)) {
                    if(entityComponentClassDecl == null)
                        throw IllegalArgumentException("${daoFunDeclaration.simpleName.asString()} has " +
                                "insert/update/delete annotation, but no entity component type")


                    pkProp = entityComponentClassDecl.entityPrimaryKeyProps.first()

                    val tableId = entityComponentClassDecl.getAnnotation(ReplicateEntity::class)?.tableId ?: 0

                    setPk = daoFunDeclaration.hasAnnotation(Insert::class)
                            && entityComponentClassDecl.isReplicateEntityWithAutoIncPrimaryKey

                    val setLastChangedProp = entityComponentClassDecl.getAllProperties().firstOrNull {
                        it.hasAnnotation(ReplicateLastModified::class)
                    }

                    if(setPk || setLastChangedProp != null) {
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
                            beginControlFlow("if($varName.${pkProp?.simpleName?.asString()} == 0L)")
                            add("val _newPk = _pkManager.nextId")
                            if(overridingFunction.isSuspended)
                                add("Async")
                            add("($tableId)\n")
                            add("$varName.${pkProp?.simpleName?.asString()} = _newPk\n")
                            if(isListParam)
                                add("_generatedPks += _newPk\n")
                            endControlFlow()
                        }

                        if(setLastChangedProp != null) {
                            add("$varName.${setLastChangedProp.simpleName.asString()} = %M()\n",
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
                    .applyIf(setPk && overridingFunction.returnType != UNIT) {
                        //We need to override this to return the PKs that were really generated
                        val entityParamType = overridingFunction.parameters.first().type
                        if(entityParamType.isListOrArray()) {
                            add("return _generatedPks")
                            if(entityParamType.isArrayType())
                                add("toTypedArray()")
                            add("\n")
                        }else {
                            add("return ${overridingFunction.parameters.first().name}.${pkProp?.simpleName?.asString()}\n")
                        }
                    }
            }
            .build())
        .build())

    return this
}

/**
 * Add a TypeSpec representing a database replication wrapper for the given database to the filespec
 */
fun FileSpec.Builder.addDbWrapperTypeSpec(
    dbClassDecl: KSClassDeclaration,
    resolver: Resolver,
    target: DoorTarget,
): FileSpec.Builder {
    val dbClassName = dbClassDecl.toClassName()
    addType(
        TypeSpec.classBuilder("${dbClassDecl.simpleName.asString()}${DoorDatabaseWrapper.SUFFIX}")
            .addOriginatingKsFileOrThrow(dbClassDecl.containingFile)
            .addOriginatingKSClasses(dbClassDecl.allDbEntities())
            .addOriginatingKSClasses(dbClassDecl.dbEnclosedDaos())
            .addAnnotation(AnnotationSpec.builder(Suppress::class)
                .addMember("%S, %S", "REDUNDANT_PROJECTION", "ClassName")
                .build())
            .superclass(dbClassName)
            .addSuperinterface(DoorDatabaseWrapper::class.asClassName().parameterizedBy(dbClassName))
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("_db", dbClassName)
                .addParameter("nodeId", LONG)
                .addParameter("messageCallback",
                    DoorMessageCallback::class.asClassName().parameterizedBy(dbClassDecl.toClassName()))
                .build())
            .addProperty(PropertySpec.builder("_db", dbClassName, KModifier.PRIVATE)
                .initializer("_db").build())
            .addProperty(PropertySpec.builder("nodeId", LONG, KModifier.OVERRIDE)
                .initializer("nodeId").build())
            .addProperty(PropertySpec.builder("messageCallback",
                    DoorMessageCallback::class.asClassName().parameterizedBy(dbClassDecl.toClassName()),
                    KModifier.PRIVATE)
                .initializer("messageCallback")
                .build()
            )
            .applyIf(target == DoorTarget.JS || target == DoorTarget.JVM) {
                addDbVersionProperty(dbClassDecl)
                addFunction(FunSpec.builder("createAllTables")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(List::class.parameterizedBy(String::class))
                    .addCode("return _db.createAllTables()\n")
                    .build())
            }
            .addProperty(PropertySpec.builder("dbName", String::class, KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder()
                    .addCode("return \"DoorWrapper for [\${_db.toString()}]\"\n")
                    .build())
                .build())
            .apply {
                dbClassDecl.allDbClassDaoGetters().forEach {  daoPropOrGetter ->
                    addDbDaoWrapperPropOrGetter(daoPropOrGetter, resolver)
                }
            }
            .addProperty(PropertySpec.builder("realDatabase", RoomDatabase::class)
                .addModifiers(KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addCode("return _db\n")
                    .build())
                .build())
            .addProperty(
                PropertySpec.builder(
                "nodeEventManager",
                    NodeEventManagerCommon::class.asClassName().parameterizedBy(dbClassName),
                    KModifier.OVERRIDE,
                )
                .applyIf(target == DoorTarget.JVM) {
                    initializer("%T(_db, messageCallback)\n", NodeEventManagerJvm::class)
                }
                .applyIf(target == DoorTarget.ANDROID) {
                    initializer("%T(_db, messageCallback)\n",
                        ClassName("com.ustadmobile.door.nodeevent", "NodeEventManagerAndroid"))
                }
                .applyIf(target == DoorTarget.JS) {
                    initializer("%T(_db, messageCallback)\n",
                        ClassName("com.ustadmobile.door.nodeevent", "NodeEventManagerJs"))
                }
                .build()
            )
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
                .addParameter("runnable", ClassName("kotlinx.coroutines",
                    "Runnable"))
                .addCode("_db.runInTransaction(runnable)\n")
                .build())
            .applyIf(target == DoorTarget.ANDROID) {
                addRoomDatabaseCreateOpenHelperFunction()
                addRoomCreateInvalidationTrackerFunction()
                addOverrideInvalidationTracker("_db")
            }
            .applyIf(target != DoorTarget.ANDROID) {
                addOverrideInvalidationTracker("_db")
            }
            .addGetDaoByClassfunction(dbClassDecl)
            .addFunction(FunSpec.builder("close")
                .addModifiers(KModifier.OVERRIDE)
                .addCode("_db.close()\n")
                .build())

            .build()
    )

    return this
}

private fun TypeSpec.Builder.addGetDaoByClassfunction(
    dbKSClassDeclaration: KSClassDeclaration,
) : TypeSpec.Builder{
    val typeVarName = TypeVariableName.Companion.invoke("T", Any::class)
    addFunction(FunSpec.builder("getDaoByClass")
        .addModifiers(KModifier.OVERRIDE)
        .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "UNCHECKED_CAST").build())
        .addTypeVariable(typeVarName)
        .addParameter("daoClass", KClass::class.asClassName().parameterizedBy(typeVarName))
        .returns(typeVarName)
        .addCode(CodeBlock.builder()
            .apply {
                beginControlFlow("return when(daoClass)")
                dbKSClassDeclaration.allDbDaos().forEach { daoDecl ->
                    add("%T::class -> ", daoDecl.toClassName())
                    add(dbKSClassDeclaration.findDbGetterForDao(daoDecl)?.toPropertyOrEmptyFunctionCaller() ?: "")
                    add(" as T\n")
                }
                add("else -> throw %T(%S)\n", ClassName("kotlin", "IllegalArgumentException"),
                        "No such DAO on this DB")
                endControlFlow()
            }
            .build())
        .apply {

        }
        .build())

    return this
}


fun FileSpec.Builder.addDaoWrapperTypeSpec(
    daoClassDeclaration: KSClassDeclaration,
    resolver: Resolver,
    target: DoorTarget,
    logger: KSPLogger,
): FileSpec.Builder {
    addType(TypeSpec.classBuilder(daoClassDeclaration.toClassNameWithSuffix(DoorDatabaseWrapper.SUFFIX))
        .addSuperClassOrInterface(daoClassDeclaration)
        .addOriginatingKSClass(daoClassDeclaration)
        .addOriginatingKSClasses(daoClassDeclaration.allDaoEntities(resolver))
        .primaryConstructor(FunSpec.constructorBuilder()
            .addParameter("_db", RoomDatabase::class)
            .addParameter("_dao", daoClassDeclaration.toClassName())
            .build())
        .addProperty(PropertySpec.builder("_db", RoomDatabase::class,
            KModifier.PRIVATE)
            .initializer("_db")
            .build())
        .addProperty(PropertySpec.builder("_dao", daoClassDeclaration.toClassName(),
            KModifier.PRIVATE)
            .initializer("_dao")
            .build())
        .apply {
            daoClassDeclaration.getAllDaoFunctionsIncSuperTypesToGenerate().forEach { daoFunDeclaration ->
                addDaoFunctionDelegate(daoFunDeclaration, daoClassDeclaration, resolver, target, logger)
            }
        }
        .build()
    )


    return this
}


/**
 * Determine if this KSClassDeclaration (or any of its ancestors) representing a DAO has any functions with the Insert,
 * Delete, or Update annotation that are acting on a ReplicateEntity
 */
private fun KSClassDeclaration.daoHasReplicateEntityWriteFunctions(
    resolver: Resolver,
): Boolean {
    val allInsertUpdateDeleteFuns = getAllFunctionsIncSuperTypes {
        it.isDaoReplicateEntityWriteFunction(resolver, this)
    }

    return allInsertUpdateDeleteFuns.isNotEmpty()
}

private fun KSFunctionDeclaration.isDaoReplicateEntityWriteFunction(
    resolver: Resolver,
    daoClassDeclaration: KSClassDeclaration,
): Boolean {
    return if(!hasAnyAnnotation(Update::class, Insert::class, Delete::class)) {
        false
    } else {
        val fnResolved = asMemberOf(daoClassDeclaration.asType(emptyList()))
        fnResolved.firstParamEntityType(resolver).declaration.hasAnnotation(ReplicateEntity::class)
    }
}

/**
 * Generate the DoorWrapper for databases and DAOs. See DoorDatabaseWrapper interface
 */
class DoorWrapperProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor{

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val target = environment.doorTarget(resolver)

        resolver.getDatabaseSymbolsToProcess().forEach { dbClassDecl ->
            FileSpec.builder(dbClassDecl.packageName.asString(),
                "${dbClassDecl.simpleName.asString()}${DoorDatabaseWrapper.SUFFIX}")
                .addDbWrapperTypeSpec(dbClassDecl, resolver, target)
                .build()
                .writeTo(environment.codeGenerator, false)
        }

        resolver.getDaoSymbolsToProcess().forEach { daoClassDec ->
            if(daoClassDec.daoHasReplicateEntityWriteFunctions(resolver)) {
                FileSpec.builder(daoClassDec.packageName.asString(),
                    "${daoClassDec.simpleName.asString()}${DoorDatabaseWrapper.SUFFIX}")
                    .addDaoWrapperTypeSpec(daoClassDec, resolver, target, environment.logger)
                    .build()
                    .writeTo(environment.codeGenerator, false)
            }
        }

        return emptyList()
    }
}
