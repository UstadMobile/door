package com.ustadmobile.lib.annotationprocessor.core

import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.room.*
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import javax.annotation.processing.*
import javax.lang.model.element.*
import javax.lang.model.type.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ustadmobile.door.*
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.util.TablesNamesFinder
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import kotlin.reflect.jvm.internal.impl.name.FqName
import kotlin.reflect.jvm.internal.impl.builtins.jvm.JavaToKotlinClassMap
import javax.lang.model.util.SimpleTypeVisitor7
import javax.tools.Diagnostic
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.annotation.*
import com.ustadmobile.door.attachments.AttachmentFilter
import com.ustadmobile.door.entities.ChangeLog
import com.ustadmobile.door.ext.DoorDatabaseMetadata
import com.ustadmobile.door.ext.DoorDatabaseMetadata.Companion.SUFFIX_DOOR_METADATA
import com.ustadmobile.door.ext.minifySql
import com.ustadmobile.door.replication.ReplicationRunOnChangeRunner
import com.ustadmobile.door.replication.ReplicationEntityMetaData
import com.ustadmobile.door.replication.ReplicationFieldMetaData
import com.ustadmobile.door.replication.ReplicationNotificationDispatcher
import com.ustadmobile.door.util.DeleteZombieAttachmentsListener
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorRepository.Companion.SUFFIX_REPOSITORY2
import kotlinx.coroutines.GlobalScope
import kotlin.reflect.KClass
import com.ustadmobile.door.util.NodeIdAuthCache
import com.ustadmobile.door.util.TransactionDepthCounter
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorJdbcKotlin.Companion.SUFFIX_JDBC_KT2
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorJdbcKotlin.Companion.SUFFIX_JS_IMPLEMENTATION_CLASSES
import com.ustadmobile.lib.annotationprocessor.core.ext.*
import io.github.aakira.napier.Napier
import java.io.File

val QUERY_SINGULAR_TYPES = listOf(INT, LONG, SHORT, BYTE, BOOLEAN, FLOAT, DOUBLE,
        String::class.asTypeName(), String::class.asTypeName().copy(nullable = true))

/**
 * Given an input result type (e.g. Entity, Entity[], List<Entity>, String, int, etc), figure out
 * what the actual entity type is
 */
fun resolveEntityFromResultType(type: TypeName) =
        if(type is ParameterizedTypeName && type.rawType.canonicalName == "kotlin.collections.List") {
            val typeArg = type.typeArguments[0]
            if(typeArg is WildcardTypeName) {
                typeArg.outTypes[0]
            }else {
                typeArg
            }
        }else {
            type
        }


//As per https://github.com/square/kotlinpoet/issues/236
internal fun TypeName.javaToKotlinType(): TypeName = if (this is ParameterizedTypeName) {
    (rawType.javaToKotlinType() as ClassName).parameterizedBy(
            *typeArguments.map { it.javaToKotlinType() }.toTypedArray()
    )
} else {
    val className = JavaToKotlinClassMap.INSTANCE
            .mapJavaToKotlin(FqName(toString()))?.asSingleFqName()?.asString()
    if (className == null) this
    else ClassName.bestGuess(className)
}


fun isContinuationParam(paramTypeName: TypeName) = paramTypeName is ParameterizedTypeName &&
        paramTypeName.rawType.canonicalName == "kotlin.coroutines.Continuation"

/**
 * Figures out the return type of a method. This will also figure out the return type of a suspended method
 */
fun resolveReturnTypeIfSuspended(method: ExecutableType) : TypeName {
    val continuationParam = method.parameterTypes.firstOrNull { isContinuationParam(it.asTypeName()) }
    return if(continuationParam != null) {
        //The continuation parameter is always the last parameter, and has one type argument
        val contReturnType = (method.parameterTypes.last() as DeclaredType).typeArguments.first().extendsBoundOrSelf().asTypeName()
        removeTypeProjection(contReturnType)
        //Open classes can result in <out T> being generated instead of just <T>. Therefor we want to remove the wildcard
    }else {
        method.returnType.asTypeName().javaToKotlinType()
    }
}

/**
 * Remove <out T>
 */
fun removeTypeProjection(typeName: TypeName) =
    if(typeName is ParameterizedTypeName && typeName.typeArguments[0] is WildcardTypeName) {
        typeName.rawType.parameterizedBy((typeName.typeArguments[0] as WildcardTypeName).outTypes[0]).javaToKotlinType()
    }else {
        typeName.javaToKotlinType()
    }


/**
 * If the return type is LiveData, Factory, etc. then unwrap that into the result type.
 */
fun resolveQueryResultType(returnTypeName: TypeName)  =
        if(returnTypeName is ParameterizedTypeName
                && returnTypeName.rawType == LiveData::class.asClassName()) {
            returnTypeName.typeArguments[0]
        }else if(returnTypeName is ParameterizedTypeName
            && returnTypeName.rawType == DataSource.Factory::class.asClassName())
            List::class.asClassName().parameterizedBy(returnTypeName.typeArguments[1])
        else {
            returnTypeName
        }

fun makeInsertAdapterMethodName(
    paramType: KSType,
    returnType: KSType?,
    async: Boolean,
    resolver: Resolver,
): String {
    var methodName = "insert"
    if(paramType.isListOrArrayType(resolver)) {
        methodName += "List"
        if(returnType != null && returnType != resolver.builtIns.unitType)
            methodName += "AndReturnIds"
    }else {
        if(returnType != null && returnType != resolver.builtIns.unitType) {
            methodName += "AndReturnId"
        }
    }

    if(async)
        methodName += "Async"

    return methodName
}

/**
 * Generate the DatabaseMetadata object for the given database.
 */
private fun FileSpec.Builder.addDatabaseMetadataType(
    dbKSClass: KSClassDeclaration,
): FileSpec.Builder {
    addType(TypeSpec.classBuilder("${dbKSClass.simpleName.asString()}$SUFFIX_DOOR_METADATA")
        .superclass(DoorDatabaseMetadata::class.asClassName().parameterizedBy(dbKSClass.toClassName()))
        .addProperty(PropertySpec.builder("dbClass", KClass::class.asClassName().parameterizedBy(dbKSClass.toClassName()))
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return %T::class\n", dbKSClass.toClassName())
                .build())
            .build())
        .addProperty(PropertySpec.builder("hasReadOnlyWrapper", Boolean::class)
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return %L\n", dbKSClass.dbHasReplicateWrapper())
                .build())
            .build())
        .addProperty(PropertySpec.builder("hasAttachments", Boolean::class)
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return %L\n", dbKSClass.allDbEntities().any { it.entityHasAttachments() })
                .build())
            .build())
        .addProperty(PropertySpec.builder("syncableTableIdMap", Map::class.parameterizedBy(String::class, Int::class))
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return TABLE_ID_MAP\n", dbKSClass.toClassNameWithSuffix(SUFFIX_REPOSITORY2))
                .build())
            .build())
        .addProperty(PropertySpec.builder("version", INT)
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return ${dbKSClass.getAnnotation(Database::class)?.version ?: -1}\n")
                .build())
            .build())
        .addProperty(PropertySpec.builder("allTables", List::class.parameterizedBy(String::class))
            .addModifiers(KModifier.OVERRIDE)
            .initializer(CodeBlock.builder()
                .add("listOf(%L)\n", dbKSClass.allDbEntities().map { it.simpleName.asString() }
                    .joinToString(prefix = "\"", postfix = "\"", separator = "\", \""))
                .build())
            .build())
        .addProperty(PropertySpec.builder("replicateEntities", Map::class.parameterizedBy(Int::class, ReplicationEntityMetaData::class))
            .addModifiers(KModifier.OVERRIDE)
            .delegate(
                CodeBlock.builder()
                    .beginControlFlow("lazy(%T.NONE)", LazyThreadSafetyMode::class)
                    .add("mapOf<%T, %T>(\n", INT, ReplicationEntityMetaData::class)
                    .apply {
                        dbKSClass.allDbEntities()
                            .filter { it.hasAnnotation(ReplicateEntity::class)}.forEach { replicateEntity ->
                                add("%L to ", replicateEntity.getAnnotation(ReplicateEntity::class)?.tableId ?: -1)
                                addReplicateEntityMetaDataCode(replicateEntity)
                                add(",\n")
                            }
                    }
                    .add(")\n")
                    .endControlFlow()
                    .build())
            .build())
        .addType(TypeSpec.companionObjectBuilder()
            .addTableIdMapProperty()
            .build())
        .build())

    return this
}

private fun FileSpec.Builder.addJsImplementationsClassesObject(
    dbKSClass: KSClassDeclaration,
) : FileSpec.Builder {
    val jsImplClassName = ClassName("com.ustadmobile.door.util", "DoorJsImplClasses")
    addType(TypeSpec.objectBuilder(dbKSClass.simpleName.asString() + SUFFIX_JS_IMPLEMENTATION_CLASSES)
        .superclass(jsImplClassName.parameterizedBy(dbKSClass.toClassName()))
        .addProperty(PropertySpec.builder("dbKClass",
            KClass::class.asClassName().parameterizedBy(dbKSClass.toClassName()))
            .addModifiers(KModifier.OVERRIDE)
            .initializer("%T::class", dbKSClass.toClassName())
            .build())
        .addProperty(PropertySpec.builder("dbImplKClass", KClass::class.asTypeName().parameterizedBy(STAR))
            .addModifiers(KModifier.OVERRIDE)
            .initializer("%T::class", dbKSClass.toClassNameWithSuffix(SUFFIX_JDBC_KT2))
            .build())
        .addProperty(PropertySpec.builder("replicateWrapperImplClass", KClass::class.asTypeName()
            .parameterizedBy(STAR).copy(nullable = true))
            .addModifiers(KModifier.OVERRIDE)
            .initializer(CodeBlock.builder()
                .apply {
                    if(dbKSClass.dbHasReplicateWrapper()) {
                        add("%T::class", dbKSClass.toClassNameWithSuffix(DoorDatabaseReplicateWrapper.SUFFIX))
                    }else {
                        add("null")
                    }
                }
                .build())
            .build())
        .addProperty(PropertySpec.builder("repositoryImplClass",
            KClass::class.asTypeName().parameterizedBy(STAR).copy(nullable = true))
            .addModifiers(KModifier.OVERRIDE)
            .initializer(CodeBlock.builder()
                .apply {
                    if(dbKSClass.dbEnclosedDaos().any { it.hasAnnotation(Repository::class) }) {
                        add("%T::class", dbKSClass.toClassNameWithSuffix(SUFFIX_REPOSITORY2))
                    }else {
                        add("null")
                    }
                }
                .build())
            .build())
        .addProperty(PropertySpec.builder("metadata",
            DoorDatabaseMetadata::class.asClassName().parameterizedBy(dbKSClass.toClassName()))
            .addModifiers(KModifier.OVERRIDE)
            .initializer("%T()", dbKSClass.toClassNameWithSuffix(SUFFIX_DOOR_METADATA))
            .build())
        .build())


    return this
}

private fun CodeBlock.Builder.addReplicateEntityMetaDataCode(
    entity: KSClassDeclaration,
): CodeBlock.Builder {

    fun CodeBlock.Builder.addFieldsCodeBlock(typeEl: KSClassDeclaration) : CodeBlock.Builder{
        add("listOf(")
        typeEl.entityProps().forEach {
            add("%T(%S, %L),", ReplicationFieldMetaData::class, it.simpleName.asString(),
                it.type.resolve().toTypeName().toSqlTypesInt())
        }
        add(")")
        return this
    }

    val repEntityAnnotation = entity.getAnnotation(ReplicateEntity::class)
    val trackerTypeEl = entity.getReplicationTracker()
    add("%T(", ReplicationEntityMetaData::class)
    add("%L, ", repEntityAnnotation?.tableId)
    add("%L, ", repEntityAnnotation?.priority)
    add("%S, ", entity.entityTableName)
    add("%S, ", trackerTypeEl.entityTableName)
    add("%S, ", entity.replicationEntityReceiveViewName)
    add("%S, ", entity.entityPrimaryKeyProps.first().simpleName.asString())
    add("%S, ", entity.firstPropWithAnnotation(ReplicationVersionId::class).simpleName.asString())
    add("%S, ", trackerTypeEl.firstPropWithAnnotation(ReplicationEntityForeignKey::class).simpleName.asString())
    add("%S, ", trackerTypeEl.firstPropWithAnnotation(ReplicationDestinationNodeId::class).simpleName.asString())
    add("%S, ", trackerTypeEl.firstPropWithAnnotation(ReplicationVersionId::class).simpleName.asString())
    add("%S, \n", trackerTypeEl.firstPropWithAnnotation(ReplicationPending::class).simpleName.asString())
    addFieldsCodeBlock(entity).add(",\n")
    addFieldsCodeBlock(trackerTypeEl).add(",\n")
    add(entity.firstPropNameWithAnnotationOrNull(AttachmentUri::class)).add(",\n")
    add(entity.firstPropNameWithAnnotationOrNull(AttachmentMd5::class)).add(",\n")
    add(entity.firstPropNameWithAnnotationOrNull(AttachmentSize::class)).add(",\n")
    add("%L", repEntityAnnotation?.batchSize ?: 1000)
    add(")")
    return this
}


private fun FileSpec.Builder.addReplicationRunOnChangeRunnerType(
    dbKSClass: KSClassDeclaration
): FileSpec.Builder {
    //find all DAOs on the database that contain a ReplicationRunOnChange annotation
    val daosWithRunOnChange = dbKSClass.dbEnclosedDaos()
        .filter { dao -> dao.getAllFunctions().any { it.hasAnnotation(ReplicationRunOnChange::class) } }
    val daosWithRunOnNewNode = dbKSClass.dbEnclosedDaos()
        .filter { dao -> dao.getAllFunctions().any { it.hasAnnotation(ReplicationRunOnNewNode::class) } }

    val allReplicateEntities = dbKSClass.allDbEntities()
        .filter { it.hasAnnotation(ReplicateEntity::class) }


    addType(TypeSpec.classBuilder(dbKSClass.toClassNameWithSuffix(DbProcessorJdbcKotlin.SUFFIX_REP_RUN_ON_CHANGE_RUNNER))
        .addSuperinterface(ReplicationRunOnChangeRunner::class)
        .addAnnotation(AnnotationSpec.builder(Suppress::class)
            .addMember("%S, %S, %S, %S", "LocalVariableName", "RedundantVisibilityModifier", "unused", "ClassName")
            .build())
        .primaryConstructor(FunSpec.constructorBuilder()
            .addParameter("_db", dbKSClass.toClassName())
            .build())
        .addProperty(PropertySpec.builder("_db", dbKSClass.toClassName(), KModifier.PRIVATE)
            .initializer("_db")
            .build())
        .apply {
            allReplicateEntities.forEach { repEntity ->
                addFunction(FunSpec.builder("handle${repEntity.simpleName.asString()}Changed")
                    .receiver(dbKSClass.toClassName())
                    .addModifiers(KModifier.SUSPEND)
                    .addModifiers(KModifier.PRIVATE)
                    .returns(Set::class.parameterizedBy(String::class))
                    .addCode(CodeBlock.builder()
                        .apply {
                            val repTablesToCheck = mutableSetOf<String>()
                            daosWithRunOnChange.forEach { dao ->
                                val daoFunsToRun = dao.getAllFunctions()
                                    .filter { daoFun ->
                                        daoFun.annotations.any { annotation ->
                                            annotation.isAnnotationClass(ReplicationRunOnChange::class)
                                                    && repEntity in annotation.getArgValueAsClassList("value")
                                        }
                                    }

                                val daoPropOrGetter = dbKSClass.findDbGetterForDao(dao)

                                daoFunsToRun.forEach { funToRun ->
                                    add(daoPropOrGetter?.toPropertyOrEmptyFunctionCaller() ?: "")
                                    add(".${funToRun.simpleName.asString()}(")
                                    if(funToRun.parameters.firstOrNull()?.hasAnnotation(NewNodeIdParam::class) == true) {
                                        add("0L")
                                    }

                                    add(")\n")
                                    val checkPendingRepTablesNames = funToRun.getKSAnnotationsByType(ReplicationCheckPendingNotificationsFor::class)
                                        .firstOrNull()?.getArgValueAsClassList("value")
                                        ?.map { it.simpleName.asString() } ?: listOf(repEntity.simpleName.asString())
                                    repTablesToCheck.addAll(checkPendingRepTablesNames)
                                }
                            }
                            add("%M(%L)\n",
                                MemberName("com.ustadmobile.door.ext", "deleteFromChangeLog"),
                                repEntity.getAnnotation(ReplicateEntity::class)?.tableId)

                            add("return setOf(${repTablesToCheck.joinToString { "\"$it\"" }})\n")
                        }
                        .build())
                    .build())
            }
        }
        .addFunction(FunSpec.builder("runReplicationRunOnChange")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("tableNames", Set::class.parameterizedBy(String::class))
            .returns(Set::class.parameterizedBy(String::class))
            .addCode(CodeBlock.builder()
                .apply {
                    add("val _checkPendingNotifications = mutableSetOf<String>()\n")
                    beginControlFlow("_db.%M(%T::class)",
                        MemberName("com.ustadmobile.door.ext", "withDoorTransactionAsync"),
                        dbKSClass.toClassName())
                    add("_transactionDb ->\n")
                    allReplicateEntities.forEach { repEntity ->
                        beginControlFlow("if(%S in tableNames)", repEntity.simpleName.asString())
                        add("_checkPendingNotifications.addAll(_transactionDb.handle${repEntity.simpleName.asString()}Changed())\n")
                        endControlFlow()
                    }
                    add("Unit\n")
                    endControlFlow()
                    add("return _checkPendingNotifications\n")
                }
                .build())
            .build())
        .addFunction(FunSpec.builder("runOnNewNode")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("newNodeId", LONG)
            .returns(Set::class.parameterizedBy(String::class))
            .addCode(CodeBlock.builder()
                .apply {
                    val entitiesChanged = mutableSetOf<String>()
                    beginControlFlow("_db.%M(%T::class)",
                        MemberName("com.ustadmobile.door.ext", "withDoorTransactionAsync"),
                        dbKSClass.toClassName())
                    add("_transactionDb -> \n")
                    add("var fnTimeCounter = 0L\n")
                    daosWithRunOnNewNode.forEach { dao ->
                        val daoAccessor = dbKSClass.findDbGetterForDao(dao)
                        dao.getAllFunctions().filter { it.hasAnnotation(ReplicationRunOnNewNode::class) }.forEach { daoFun ->
                            add("fnTimeCounter = %M()\n",
                                MemberName("com.ustadmobile.door.util", "systemTimeInMillis"))
                            add("_transactionDb.%L.${daoFun.simpleName.asString()}(newNodeId)\n",
                                daoAccessor?.toPropertyOrEmptyFunctionCaller())
                            add("%T.d(%S + (%M() - fnTimeCounter) + %S)\n", Napier::class,
                                "Ran ${dao.simpleName}#${daoFun.simpleName} in ",
                                MemberName("com.ustadmobile.door.util", "systemTimeInMillis"),
                                "ms")
                            val funEntitiesChanged = daoFun.getKSAnnotationsByType(ReplicationCheckPendingNotificationsFor::class)
                                .firstOrNull()
                                ?.getArgValueAsClassList("value") ?: emptyList()

                            entitiesChanged += funEntitiesChanged.map { it.entityTableName }
                        }
                    }
                    endControlFlow()
                    add("return setOf(${entitiesChanged.joinToString(separator = "\", \"", prefix = "\"", postfix = "\"")})\n")
                }
                .build())
            .build())
        .build())

    return this
}


/**
 * This class represents a POKO object in the result. It is a tree like structure (e.g. each item
 * has oen parent, and can have multiple children). The root object has no parent.
 *
 * This class gathers up all properties from the class itself and ancestors. It will also recursively
 * search through any fields with the @Embedded annotation
 */
class ResultEntityField(val parentField: ResultEntityField?, val name: String,
                        val type: TypeName, val embeddedType: TypeElement?,
                        processingEnv: ProcessingEnvironment){

    val childFields: List<ResultEntityField>

    init {
        if(embeddedType != null) {
            val ancestorClasses = ancestorsToList(embeddedType, processingEnv)
            val allFields = ancestorClasses
                    .flatMap { it.enclosedElements.filter {
                        it.kind == ElementKind.FIELD  && Modifier.STATIC !in it.modifiers && Modifier.TRANSIENT !in it.modifiers
                    } }

            childFields = allFields.map { ResultEntityField(this, it.simpleName.toString(),
                    it.asType().asTypeName(),
                    if(it.getAnnotation(Embedded::class.java) != null) {
                        processingEnv.typeUtils.asElement(it.asType()) as TypeElement?
                    }else {
                        null
                    }, processingEnv)
            }
        }else {
            childFields = listOf()
        }
    }

    fun createSetterCodeBlock(rawQuery: Boolean = false, resultSetVarName: String = "_resultSet",
                              colIndexVarName: String? = null): CodeBlock {
        val codeBlock = CodeBlock.builder()
        val checkForNullEmbeddedObject = parentField != null
        val nullFieldCountVarName = "_${name}_nullFieldCount"

        if(checkForNullEmbeddedObject) {
            codeBlock.add("var $nullFieldCountVarName = 0\n")
        }
        childFields.filter { it.embeddedType == null }.forEach {
            val getterName = "get${it.type.preparedStatementSetterGetterTypeName}"
            codeBlock.add("val tmp_${it.name}")

            val setValAndCheckForNullBlock = CodeBlock.builder().add(" = ${resultSetVarName}.$getterName(%S)\n",
                    it.name)
            if(checkForNullEmbeddedObject){
                setValAndCheckForNullBlock.add("if(${resultSetVarName}.wasNull())·{·$nullFieldCountVarName++·}\n")
            }

            if(rawQuery) {
                val valType = it.type.javaToKotlinType()
                codeBlock.add(": %T\n", valType.copy(nullable = valType == String::class.asClassName()))
                        .beginControlFlow("if($colIndexVarName.containsKey(%S))", it.name)
                        .add("tmp_${it.name} ").add(setValAndCheckForNullBlock.build())
                        .nextControlFlow("else")
                if(checkForNullEmbeddedObject) {
                    codeBlock.add("$nullFieldCountVarName++\n")
                }
                codeBlock.add("tmp_${it.name} = ${defaultVal(it.type)}\n")
                        .endControlFlow()
            }else {
                codeBlock.add(setValAndCheckForNullBlock.build())
            }
        }

        if(checkForNullEmbeddedObject) {
            codeBlock.beginControlFlow("if($nullFieldCountVarName < ${childFields.size})")
        }

        //check the embedded path of parents
        val parentsList = mutableListOf<ResultEntityField>(this)


        var nextParent = parentField
        while(nextParent != null) {
            parentsList.add(nextParent)
            nextParent = nextParent.parentField
        }


        parentsList.reverse()
        val rootItem = parentsList[0]
        var entityPath  = ""

        if(parentField == null) {
            //this is the root item
            codeBlock.add("val $name = %T()\n", embeddedType)
            entityPath = name
        }else {
            val predecessorsList = parentsList.subList(1, parentsList.size)
            predecessorsList.forEachIndexed { index, parent ->
                val predecessors = predecessorsList.subList(0, index + 1)
                entityPath = "${rootItem.name}.${predecessors.joinToString(separator = "!!.") { it.name }}"
                codeBlock.beginControlFlow("if($entityPath == null)")
                        .add("$entityPath = %T()\n", parent.embeddedType)
                        .endControlFlow()
                entityPath += "!!"
            }
        }

        childFields.filter { it.embeddedType == null }.forEach {
            codeBlock.add("${entityPath}.${it.name} = tmp_${it.name}\n")
        }


        if(checkForNullEmbeddedObject) {
            codeBlock.endControlFlow()
        }

        childFields.filter { it.embeddedType != null }.forEach {
            codeBlock.add(it.createSetterCodeBlock(rawQuery = rawQuery, resultSetVarName = resultSetVarName,
                    colIndexVarName = colIndexVarName))
        }

        return codeBlock.build()
    }

}

/**
 *
 */
internal fun ancestorsToList(child: TypeElement, processingEnv: ProcessingEnvironment): List<TypeElement> {
    val entityAncestors = mutableListOf<TypeElement>()

    var nextEntity = child as TypeElement?

    do {
        entityAncestors.add(nextEntity!!)
        val nextElement = processingEnv.typeUtils.asElement(nextEntity.superclass)
        nextEntity = if(nextElement is TypeElement && nextElement.qualifiedName.toString() != "java.lang.Object") {
            nextElement
        } else {
            null
        }
    }while(nextEntity != null)

    return entityAncestors
}

fun defaultVal(typeName: TypeName) : CodeBlock {
    val codeBlock = CodeBlock.builder()
    val kotlinType = typeName.javaToKotlinType()
    when(kotlinType) {
        INT -> codeBlock.add("0")
        LONG -> codeBlock.add("0L")
        BYTE -> codeBlock.add("0.toByte()")
        FLOAT -> codeBlock.add("0.toFloat()")
        DOUBLE -> codeBlock.add("0.toDouble()")
        BOOLEAN -> codeBlock.add("false")
        String::class.asTypeName() -> codeBlock.add("null as String?")
        else -> {
            if(kotlinType is ParameterizedTypeName && kotlinType.rawType == List::class.asClassName()) {
                val typeArg = if(kotlinType.typeArguments[0] == String::class.asTypeName()) {
                    kotlinType.typeArguments[0].copy(nullable = true)
                }else {
                    kotlinType.typeArguments[0]
                }

                codeBlock.add("mutableListOf<%T>()", typeArg)
            }else {
                codeBlock.add("null as %T?", typeName)
            }
        }
    }

    return codeBlock.build()
}

@Deprecated("Should use extension function for this")
fun isListOrArray(typeName: TypeName) = (typeName is ClassName && typeName.canonicalName =="kotlin.Array")
        || (typeName is ParameterizedTypeName && typeName.rawType == List::class.asClassName())

val SQL_COMPONENT_TYPE_MAP = mapOf(LONG to "BIGINT",
        INT to "INTEGER",
        SHORT to "SMALLINT",
        BOOLEAN to "BOOLEAN",
        FLOAT to "FLOAT",
        DOUBLE to "DOUBLE",
        String::class.asClassName() to "TEXT")

fun sqlArrayComponentTypeOf(typeName: TypeName): String {
    if(typeName is ParameterizedTypeName) {
        return SQL_COMPONENT_TYPE_MAP.get(typeName.typeArguments[0])!!
    }

    return "UNKNOWN"
}

//Limitation: this does not currently support interface inheritence
data class MethodToImplement(val methodName: String, val paramTypes: List<TypeName>)

fun isMethodImplemented(method: ExecutableElement, enclosingClass: TypeElement, processingEnv: ProcessingEnvironment): Boolean {
    val enclosingClassType = enclosingClass.asType() as DeclaredType
    val methodResolved = processingEnv.typeUtils.asMemberOf(enclosingClassType,
            method) as ExecutableType
    return ancestorsToList(enclosingClass, processingEnv).any {
        it.enclosedElements.any {
            it is ExecutableElement
                    && !(Modifier.ABSTRACT in it.modifiers)
                    && it.simpleName == method.simpleName
                    && processingEnv.typeUtils.isSubsignature(methodResolved,
                        processingEnv.typeUtils.asMemberOf(enclosingClassType, it) as ExecutableType)
        }
    }
}

// As per
// https://android.googlesource.com/platform/frameworks/support/+/androidx-master-dev/room/compiler/src/main/kotlin/androidx/room/ext/element_ext.kt
// converts ? in Set< ? extends Foo> to Foo
fun TypeMirror.extendsBound(): TypeMirror? {
    return this.accept(object : SimpleTypeVisitor7<TypeMirror?, Void?>() {
        override fun visitWildcard(type: WildcardType, ignored: Void?): TypeMirror? {
            return type.extendsBound ?: type.superBound
        }
    }, null)
}
/**
 * If the type mirror is in form of ? extends Foo, it returns Foo; otherwise, returns the TypeMirror
 * itself.
 */
fun TypeMirror.extendsBoundOrSelf(): TypeMirror {
    return extendsBound() ?: this
}


/**
 * Determine if the result type is nullable. Any single result entity object or String result can be
 * null (e.g. no such object was found by the query). Primitives cannot be null as they will be 0/false.
 * Lists and arrays (parameterized types) cannot be null: no results will provide an non-null empty
 * list/array.
 */
fun isNullableResultType(typeName: TypeName) = typeName != UNIT
        && !PRIMITIVE.contains(typeName)
        && !(typeName is ParameterizedTypeName)


val PRIMITIVE = listOf(INT, LONG, BOOLEAN, SHORT, BYTE, FLOAT, DOUBLE)

fun FileSpec.Builder.addJdbcDbImplType(
    dbKSClass: KSClassDeclaration,
    target: DoorTarget,
    resolver: Resolver,
) : FileSpec.Builder {
    addImport("com.ustadmobile.door.util", "systemTimeInMillis")
    addType(TypeSpec.classBuilder(dbKSClass.toClassNameWithSuffix(SUFFIX_JDBC_KT2))
        .superclass(dbKSClass.toClassName())
        .addSuperinterface(DoorDatabaseJdbc::class)
        .addSuperinterface(RoomJdbcImpl::class)
        .primaryConstructor(FunSpec.constructorBuilder()
            .addParameter("doorJdbcSourceDatabase", RoomDatabase::class.asTypeName().copy(nullable = true))
            .addParameter(ParameterSpec("dataSource", AbstractDbProcessor.CLASSNAME_DATASOURCE))
            .addParameter("dbName", String::class)
            .applyIf(target == DoorTarget.JVM) {
                addParameter("attachmentDir", File::class.asTypeName().copy(nullable = true))
            }
            .addParameter("realAttachmentFilters", List::class.parameterizedBy(AttachmentFilter::class))
            .addParameter("jdbcQueryTimeout", Int::class)
            .build())
        .addDbVersionProperty(dbKSClass)
        .addProperty(PropertySpec.builder("dataSource", AbstractDbProcessor.CLASSNAME_DATASOURCE, KModifier.OVERRIDE)
            .initializer("dataSource")
            .build())
        .addProperty(PropertySpec.builder("jdbcImplHelper", RoomDatabaseJdbcImplHelper::class, KModifier.OVERRIDE)
            .initializer("%T(dataSource, this, this::class.%M().allTables)\n", RoomDatabaseJdbcImplHelper::class,
                MemberName("com.ustadmobile.door.ext", "doorDatabaseMetadata"))
            .build())
        .applyIf(target == DoorTarget.JVM) {
            addProperty(PropertySpec.builder("realAttachmentStorageUri",
                DoorUri::class.asTypeName().copy(nullable = true))
                .addModifiers(KModifier.OVERRIDE)
                .initializer("attachmentDir?.%M()\n",
                    MemberName("com.ustadmobile.door.ext", "toDoorUri"))
                .build())
        }
        .applyIf(target == DoorTarget.JS) {
            addProperty(PropertySpec.builder("realAttachmentStorageUri",
                DoorUri::class.asTypeName().copy(nullable = true))
                .addModifiers(KModifier.OVERRIDE)
                .initializer("null")
                .build())
        }
        .addProperty(PropertySpec.builder("realAttachmentFilters",
            List::class.parameterizedBy(AttachmentFilter::class))
            .addModifiers(KModifier.OVERRIDE)
            .initializer("realAttachmentFilters")
            .build())
        .addProperty(PropertySpec.builder("doorJdbcSourceDatabase",
            RoomDatabase::class.asTypeName().copy(nullable = true), KModifier.OVERRIDE)
            .initializer("doorJdbcSourceDatabase")
            .build())
        .addProperty(PropertySpec.builder("dbName", String::class, KModifier.OVERRIDE)
            .initializer("dbName")
            .build())
        .addProperty(PropertySpec.builder("jdbcQueryTimeout", Int::class, KModifier.OVERRIDE)
            .initializer("jdbcQueryTimeout")
            .build())
        .addProperty(PropertySpec.builder("transactionDepthCounter", TransactionDepthCounter::class)
            .addModifiers(KModifier.OVERRIDE)
            .initializer("%T()\n", TransactionDepthCounter::class)
            .build())
        .addProperty(PropertySpec.builder("invalidationTracker", InvalidationTracker::class)
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return jdbcImplHelper.invalidationTracker\n")
                .build())
            .build())
        .applyIf(target == DoorTarget.JS) {
            addProperty(PropertySpec.builder("isInTransaction", Boolean::class,
                KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder()
                    .addCode("return false\n")
                    .build())
                .build())
        }
        .addProperty(PropertySpec.builder("realReplicationNotificationDispatcher",
            ReplicationNotificationDispatcher::class)
            .addModifiers(KModifier.OVERRIDE)
            .delegate(CodeBlock.builder()
                .beginControlFlow("lazy")
                .beginControlFlow("if(this == %M)",
                    MemberName("com.ustadmobile.door.ext", "rootDatabase"))
                .add("%T(this, %T(this), %T)\n", ReplicationNotificationDispatcher::class,
                    dbKSClass.toClassNameWithSuffix(DbProcessorJdbcKotlin.SUFFIX_REP_RUN_ON_CHANGE_RUNNER),
                        GlobalScope::class)
                .nextControlFlow("else")
                .add("rootDatabase.%M\n",
                    MemberName("com.ustadmobile.door.ext", "replicationNotificationDispatcher"))
                .endControlFlow()
                .endControlFlow()
                .build())
            .build())
        .addProperty(PropertySpec.builder("_deleteZombieAttachmentsListener",
            DeleteZombieAttachmentsListener::class.asTypeName().copy(nullable = true))
            .addModifiers(KModifier.PRIVATE)
            .initializer("if(this == %M) { %T(this) } else { null }",
                MemberName("com.ustadmobile.door.ext", "rootDatabase"),
                DeleteZombieAttachmentsListener::class)
            .build())
        .addProperty(PropertySpec.builder("realIncomingReplicationListenerHelper",
            IncomingReplicationListenerHelper::class)
            .addModifiers(KModifier.OVERRIDE)
            .initializer(CodeBlock.of("%T()\n", IncomingReplicationListenerHelper::class))
            .build())
        .addProperty(PropertySpec.builder("realNodeIdAuthCache", NodeIdAuthCache::class,
            KModifier.OVERRIDE)
            .delegate(CodeBlock.builder()
                .beginControlFlow("lazy")
                .beginControlFlow("if(this == %M)",
                    MemberName("com.ustadmobile.door.ext", "rootDatabase"))
                .add("val nodeIdAuthCache = %T(this)\n", NodeIdAuthCache::class)
                .add("nodeIdAuthCache.addNewNodeListener(realReplicationNotificationDispatcher)\n")
                .add("nodeIdAuthCache\n")
                .nextControlFlow("else")
                .add("rootDatabase.%M\n", MemberName("com.ustadmobile.door.ext", "nodeIdAuthCache"))
                .endControlFlow()
                .endControlFlow()
                .build())
            .build())
        .addProperty(PropertySpec.builder("realPrimaryKeyManager", DoorPrimaryKeyManager::class,
            KModifier.OVERRIDE)
            .delegate(CodeBlock.builder()
                .beginControlFlow("lazy")
                .beginControlFlow("if(isInTransaction)")
                .add("throw %T(%S)\n", ClassName("kotlin", "IllegalStateException"),
                    "doorPrimaryKeyManager must be used on root database ONLY, not transaction wrapper!")
                .endControlFlow()
                .add("%T(%T::class.%M().replicateEntities.keys)\n", DoorPrimaryKeyManager::class,
                    dbKSClass.toClassName(), MemberName("com.ustadmobile.door.ext", "doorDatabaseMetadata"))
                .endControlFlow()
                .build())
            .build())
        .addCreateAllTablesFunction(dbKSClass, resolver)
        .addClearAllTablesFunction(dbKSClass, target)
        .apply {
            dbKSClass.allDbClassDaoGetters().forEach { daoGetterOrProp ->
                val daoKSClass = daoGetterOrProp.propertyOrReturnType()?.resolve()?.declaration as? KSClassDeclaration
                    ?: return@forEach
                val daoImplClassName = daoKSClass.toClassNameWithSuffix(SUFFIX_JDBC_KT2)
                addProperty(PropertySpec.builder("_${daoKSClass.simpleName.asString()}",
                    daoImplClassName).delegate("lazy·{·%T(this)·}", daoImplClassName).build())
                addDaoPropOrGetterOverride(daoGetterOrProp, CodeBlock.of("return _${daoKSClass.simpleName.asString()}"))
            }
        }
        .build())
    return this
}


/**
 * Add a ReceiveView for the given EntityTypeElement.
 */
private fun CodeBlock.Builder.addCreateReceiveView(
    entityKSClass: KSClassDeclaration,
    sqlListVar: String
): CodeBlock.Builder {
    val trackerKSClass = entityKSClass.getReplicationTracker()
    val receiveViewAnn = entityKSClass.getAnnotation(ReplicateReceiveView::class)
    val viewName = receiveViewAnn?.name ?: "${entityKSClass.entityTableName}${AbstractDbProcessor.SUFFIX_DEFAULT_RECEIVEVIEW}"
    val sql = receiveViewAnn?.value ?: """
            SELECT ${entityKSClass.entityTableName}.*, ${trackerKSClass.entityTableName}.*
              FROM ${entityKSClass.entityTableName}
                   LEFT JOIN ${trackerKSClass.entityTableName} ON ${trackerKSClass.entityTableName}.${trackerKSClass.replicationTrackerForeignKey.simpleName.asString()} = 
                        ${entityKSClass.entityTableName}.${entityKSClass.entityPrimaryKeyProps.first().simpleName.asString()}
        """.minifySql()
    add("$sqlListVar += %S\n", "CREATE VIEW $viewName AS $sql")
    return this
}

fun TypeSpec.Builder.addCreateAllTablesFunction(
    dbKSClass: KSClassDeclaration,
    resolver: Resolver,
) : TypeSpec.Builder {
    val initDbVersion = dbKSClass.getAnnotation(Database::class)?.version ?: -1
    Napier.d("Door Wrapper: add create all tables function for ${dbKSClass.simpleName.asString()}")
    addFunction(FunSpec.builder("createAllTables")
        .addModifiers(KModifier.OVERRIDE)
        .returns(List::class.parameterizedBy(String::class))
        .addCode(CodeBlock.builder()
            .add("val _stmtList = %M<String>()\n", AbstractDbProcessor.MEMBERNAME_MUTABLE_LINKEDLISTOF)
            .beginControlFlow("when(jdbcImplHelper.dbType)")
            .apply {
                for(dbProductType in DoorDbType.SUPPORTED_TYPES) {
                    val dbTypeName = DoorDbType.PRODUCT_INT_TO_NAME_MAP[dbProductType] as String
                    beginControlFlow("$dbProductType -> ")
                    add("// - create for this $dbTypeName \n")
                    add("_stmtList += \"CREATE·TABLE·IF·NOT·EXISTS·${DoorConstants.DBINFO_TABLENAME}" +
                            "·(dbVersion·int·primary·key,·dbHash·varchar(255))\"\n")
                    add(" _stmtList += \"INSERT·INTO·${DoorConstants.DBINFO_TABLENAME}·" +
                            "VALUES·($initDbVersion,·'')\"\n")

                    //All entities MUST be created first, triggers etc. can only be created after all entities exist
                    val createEntitiesCodeBlock = CodeBlock.builder()
                    val createTriggersAndViewsBlock = CodeBlock.builder()

                    dbKSClass.allDbEntities().forEach { entityKSClass ->
                        val fieldListStr = entityKSClass.entityProps().joinToString { it.simpleName.asString() }
                        createEntitiesCodeBlock.add("//Begin: Create table ${entityKSClass.entityTableName} for $dbTypeName\n")
                            .add("/* START MIGRATION: \n")
                            .add("_stmt.executeUpdate(%S)\n",
                                "ALTER TABLE ${entityKSClass.entityTableName} RENAME to ${entityKSClass.entityTableName}_OLD")
                            .add("END MIGRATION */\n")
                            .addCreateTableCode(entityKSClass, "_stmt.executeUpdate", dbProductType,
                                sqlListVar = "_stmtList", resolver = resolver)
                            .add("/* START MIGRATION: \n")
                            .add("_stmt.executeUpdate(%S)\n", "INSERT INTO ${entityKSClass.entityTableName} ($fieldListStr) " +
                                    "SELECT $fieldListStr FROM ${entityKSClass.entityTableName}_OLD")
                            .add("_stmt.executeUpdate(%S)\n", "DROP TABLE ${entityKSClass.entityTableName}_OLD")
                            .add("END MIGRATION*/\n")


                        if(entityKSClass.hasAnnotation(ReplicateEntity::class)) {
                            createTriggersAndViewsBlock
                                .addReplicateEntityChangeLogTrigger(entityKSClass, "_stmtList", dbProductType)
                                .addCreateReceiveView(entityKSClass, "_stmtList")
                        }

                        createTriggersAndViewsBlock.addCreateTriggersCode(entityKSClass, "_stmtList",
                            dbProductType)


                        if(entityKSClass.entityHasAttachments()) {
                            if(dbProductType == DoorDbType.SQLITE) {
                                createTriggersAndViewsBlock.addGenerateAttachmentTriggerSqlite(entityKSClass,
                                    "_stmt.executeUpdate", "_stmtList")
                            }else {
                                createTriggersAndViewsBlock.addGenerateAttachmentTriggerPostgres(
                                    entityKSClass, "_stmtList")
                            }
                        }

                        createEntitiesCodeBlock.add("//End: Create table ${entityKSClass.entityTableName} for $dbTypeName\n\n")
                    }
                    add(createEntitiesCodeBlock.build())
                    add(createTriggersAndViewsBlock.build())

                    endControlFlow()
                }
            }
            .endControlFlow()
            .add("return _stmtList\n")
            .build())
        .build())

    Napier.d("Door Wrapper: done with tables function for ${dbKSClass.simpleName}")
    return this
}


/**
 * Add triggers that will insert into the ChangeLog table
 */
fun CodeBlock.Builder.addReplicateEntityChangeLogTrigger(
    entityKSClass: KSClassDeclaration,
    sqlListVar: String,
    dbProductType: Int,
) : CodeBlock.Builder{
    val replicateEntity = entityKSClass.getAnnotation(ReplicateEntity::class)
        ?: throw IllegalArgumentException("addReplicateEntitychangeLogTrigger: entity has no @ReplicateEntity annotation")
    val primaryKeyEl = entityKSClass.entityPrimaryKeyProps.first() //ReplicateEntity must have one and only one primary key

    data class TriggerParams(val opName: String, val prefix: String, val opCode: Int) {
        val opPrefix = opName.lowercase().substring(0, 3)
    }

    if(dbProductType == DoorDbType.SQLITE) {
        val triggerParams = listOf(
            TriggerParams("INSERT", "NEW", ChangeLog.CHANGE_UPSERT),
            TriggerParams("UPDATE", "NEW", ChangeLog.CHANGE_UPSERT),
            TriggerParams("DELETE", "OLD", ChangeLog.CHANGE_DELETE)
        )

        triggerParams.forEach { params ->
            /*
            Note: REPLACE INTO etc. does not work because the conflict policy will be determined by the statement
            triggering this as per https://sqlite.org/lang_createtrigger.html Section 2.
            "An ON CONFLICT clause may be specified as part of an UPDATE or INSERT action within the body of the
            trigger. However if an ON CONFLICT clause is specified as part of the statement causing the trigger to
             fire, then conflict handling policy of the outer statement is used instead."
             */
            add("$sqlListVar += %S\n",
                """
                CREATE TRIGGER ch_${params.opPrefix}_${replicateEntity.tableId}
                       AFTER ${params.opName} ON ${entityKSClass.entityTableName}
                BEGIN
                       INSERT INTO ChangeLog(chTableId, chEntityPk, chType)
                       SELECT ${replicateEntity.tableId} AS chTableId, 
                              ${params.prefix}.${primaryKeyEl.simpleName.asString()} AS chEntityPk, 
                              ${params.opCode} AS chType
                        WHERE NOT EXISTS(
                              SELECT chTableId 
                                FROM ChangeLog 
                               WHERE chTableId = ${replicateEntity.tableId}
                                 AND chEntityPk = ${params.prefix}.${primaryKeyEl.simpleName.asString()}); 
                END
                """.minifySql())
        }
    }else {
        val triggerParams = listOf(
            TriggerParams("UPDATE OR INSERT", "NEW", ChangeLog.CHANGE_UPSERT),
            TriggerParams("DELETE", "OLD", ChangeLog.CHANGE_DELETE))
        triggerParams.forEach { params ->
            add("$sqlListVar += %S\n",
                """
               CREATE OR REPLACE FUNCTION 
               ch_${params.opPrefix}_${replicateEntity.tableId}_fn() RETURNS TRIGGER AS $$
               BEGIN
               INSERT INTO ChangeLog(chTableId, chEntityPk, chType)
                       VALUES (${replicateEntity.tableId}, ${params.prefix}.${primaryKeyEl.simpleName.asString()}, ${params.opCode})
               ON CONFLICT(chTableId, chEntityPk) DO UPDATE
                       SET chType = ${params.opCode};
               RETURN NULL;
               END $$
               LANGUAGE plpgsql         
            """.minifySql())
            add("$sqlListVar += %S\n",
                """
            CREATE TRIGGER ch_${params.opPrefix}_${replicateEntity.tableId}_trig 
                   AFTER ${params.opName} ON ${entityKSClass.entityTableName}
                   FOR EACH ROW
                   EXECUTE PROCEDURE ch_${params.opPrefix}_${replicateEntity.tableId}_fn();
            """.minifySql())
        }


    }

    return this
}

private fun CodeBlock.Builder.addCreateTriggersCode(
    entityKSClass: KSClassDeclaration,
    stmtListVar: String,
    dbProductType: Int
): CodeBlock.Builder {
    Napier.d("Door Wrapper: addCreateTriggersCode ${entityKSClass.simpleName.asString()}")
    entityKSClass.getAnnotations(Triggers::class).firstOrNull()?.value?.forEach { trigger ->
        trigger.toSql(entityKSClass, dbProductType).forEach { sqlStr ->
            add("$stmtListVar += %S\n", sqlStr)
        }
    }

    return this
}

private fun TypeSpec.Builder.addClearAllTablesFunction(
    dbKSClass: KSClassDeclaration,
    target: DoorTarget
) : TypeSpec.Builder {

    addFunction(FunSpec.builder("makeClearAllTablesSql")
        .returns(List::class.parameterizedBy(String::class))
        .addCode("val _stmtList = mutableListOf<String>()\n")
        .apply {
            dbKSClass.allDbEntities().forEach {  entityType ->
                addCode("_stmtList += %S\n", "DELETE FROM ${entityType.entityTableName}")
            }
        }
        .addCode("return _stmtList\n")
        .build())
    addFunction(FunSpec.builder("clearAllTables")
        .addModifiers(KModifier.OVERRIDE)
        .applyIf(target == DoorTarget.JVM) {
            addCode("%M(*makeClearAllTablesSql().%M())\n",
                MemberName("com.ustadmobile.door.ext", "execSqlBatch"),
                MemberName("kotlin.collections", "toTypedArray"))
        }
        .applyIf(target == DoorTarget.JS) {
            addCode("throw %T(%S)\n", AbstractDbProcessor.CLASSNAME_ILLEGALSTATEEXCEPTION,
                "clearAllTables synchronous not supported on Javascript")
        }
        .build())

    if(target == DoorTarget.JS) {
        addFunction(FunSpec.builder("clearAllTablesAsync")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addCode("execSQLBatchAsyncJs(*makeClearAllTablesSql().%M())\n",
                MemberName("kotlin.collections", "toTypedArray"))
            .build())
    }
    return this
}

fun CodeBlock.Builder.addDaoJdbcInsertCodeBlock(
    daoKSFun: KSFunctionDeclaration,
    daoKSClass: KSClassDeclaration,
    daoTypeBuilder: TypeSpec.Builder,
    resolver: Resolver,
    target: DoorTarget,
): CodeBlock.Builder {
    val daoFunction = daoKSFun.asMemberOf(daoKSClass.asType(emptyList()))
    val paramType = daoFunction.parameterTypes.first()
        ?: throw IllegalArgumentException("${daoKSFun.simpleName.asString()} has no param type")
    val entityKSClass = daoFunction.firstParamEntityType(resolver).declaration as KSClassDeclaration
    val pgOnConflict = daoKSFun.getAnnotation(PgOnConflict::class)?.value
    val pgOnConflictHash = pgOnConflict?.hashCode()?.let { Math.abs(it) }?.toString() ?: ""
    val upsertMode = daoKSFun.getAnnotation(Insert::class)?.onConflict == OnConflictStrategy.REPLACE
    val entityInserterPropName = "_insertAdapter${entityKSClass.simpleName.asString()}_${if(upsertMode) "upsert" else ""}$pgOnConflictHash"
    if(!daoTypeBuilder.propertySpecs.any { it.name == entityInserterPropName }) {
        daoTypeBuilder.addDaoJdbcEntityInsertAdapter(entityKSClass, entityInserterPropName, upsertMode, target,
            pgOnConflict, resolver)
    }

    if(daoFunction.hasReturnType(resolver)) {
        add("val _retVal = ")
    }


    val insertMethodName = makeInsertAdapterMethodName(paramType, daoFunction.returnType,
        daoKSFun.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.SUSPEND), resolver)
    add("$entityInserterPropName.$insertMethodName(${daoKSFun.parameters.first().name?.asString()})")

    val returnType = daoKSFun.returnType?.resolve()
    if(daoFunction.hasReturnType(resolver)) {
        if(returnType?.isListOrArrayType(resolver) == true
                && returnType.unwrapComponentTypeIfListOrArray(resolver) == resolver.builtIns.intType) {
            add(".map { it.toInt() }")
        }else if(returnType == resolver.builtIns.intType) {
            add(".toInt()")
        }
    }

    add("\n")

    if(daoFunction.hasReturnType(resolver)) {
        add("return _retVal")

        if(returnType?.isArrayType() == true) {
            add(".toTypedArray()")
        }else if(returnType?.isLongArray() == true) {
            add(".toLongArray()")
        }else if(returnType?.isIntArray() == true) {
            add(".toIntArray()")
        }
    }

    return this
}

/**
 * Genetes an EntityInsertAdapter for use with JDBC code
 */
fun TypeSpec.Builder.addDaoJdbcEntityInsertAdapter(
    entityKSClass: KSClassDeclaration,
    propertyName: String,
    upsertMode: Boolean,
    target: DoorTarget,
    pgOnConflict: String? = null,
    resolver: Resolver,
) : TypeSpec.Builder {
    val entityFields = entityKSClass.entityProps(false)
    val entityClassName = entityKSClass.toClassName()
    val pkFields = entityKSClass.entityPrimaryKeyProps
    val insertAdapterSpec = TypeSpec.anonymousClassBuilder()
        .superclass(EntityInsertionAdapter::class.asClassName().parameterizedBy(entityKSClass.toClassName()))
        .addSuperclassConstructorParameter("_db")
        .addFunction(FunSpec.builder("makeSql")
            .addParameter("returnsId", BOOLEAN)
            .addModifiers(KModifier.OVERRIDE)
            .addCode(CodeBlock.builder()
                .apply {
                    if(target.supportedDbs.size != 1) {
                        beginControlFlow("return when(dbType)")
                    }else{
                        add("return ")
                    }
                }
                .applyIf(target.supportedDbs.size != 1 && DoorDbType.SQLITE in target.supportedDbs) {
                    beginControlFlow("%T.SQLITE ->", DoorDbType::class)
                }
                .applyIf(DoorDbType.SQLITE in target.supportedDbs) {
                    var insertSql = "INSERT "
                    if(upsertMode)
                        insertSql += "OR REPLACE "
                    insertSql += "INTO ${entityKSClass.entityTableName} (${entityFields.joinToString { it.entityPropColumnName }}) "
                    insertSql += "VALUES(${entityFields.joinToString { "?" }})"
                    add("%S", insertSql)
                }
                .applyIf(target.supportedDbs.size != 1 && DoorDbType.SQLITE in target.supportedDbs) {
                    add("\n")
                    endControlFlow()
                }
                .applyIf(target.supportedDbs.size != 1 && DoorDbType.POSTGRES in target.supportedDbs) {
                    beginControlFlow("%T.POSTGRES -> ", DoorDbType::class)
                }
                .applyIf(DoorDbType.POSTGRES in target.supportedDbs) {
                    var insertSql = "INSERT "
                    insertSql += "INTO ${entityKSClass.entityTableName} (${entityFields.joinToString { it.entityPropColumnName }}) "
                    insertSql += "VALUES("
                    insertSql += entityFields.joinToString { prop ->
                        if(prop.getAnnotation(PrimaryKey::class)?.autoGenerate == true) {
                            "COALESCE(?,nextval('${entityKSClass.entityTableName}_${prop.entityPropColumnName}_seq'))"
                        }else {
                            "?"
                        }
                    }
                    insertSql += ")"

                    when {
                        pgOnConflict != null -> {
                            insertSql += pgOnConflict.replace(" ", " ")
                        }
                        upsertMode -> {
                            insertSql += " ON CONFLICT (${pkFields.joinToString {it.entityPropColumnName } }) DO UPDATE SET "
                            insertSql += entityFields.filter { it !in pkFields }
                                .joinToString(separator = ",") {
                                    "${it.entityPropColumnName} = excluded.${it.entityPropColumnName}"
                                }
                        }
                    }
                    add("%S·+·if(returnsId)·{·%S·}·else·\"\"·", insertSql, " RETURNING ${pkFields.first().entityPropColumnName}")
                }
                .applyIf(target.supportedDbs.size != 1 && DoorDbType.POSTGRES in target.supportedDbs) {
                    add("\n")
                    endControlFlow()
                }
                .apply {
                    if(target.supportedDbs.size != 1) {
                        beginControlFlow("else ->")
                        add("throw %T(%S)\n", AbstractDbProcessor.CLASSNAME_ILLEGALARGUMENTEXCEPTION, "Unsupported db type")
                        endControlFlow()
                        endControlFlow()
                    }else {
                        add("\n")
                    }
                }
                .build())
            .build())
        .addFunction(FunSpec.builder("bindPreparedStmtToEntity")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("stmt", AbstractDbProcessor.CLASSNAME_PREPARED_STATEMENT)
            .addParameter("entity", entityClassName)
            .addCode(CodeBlock.builder()
                .apply {
                    entityFields.forEachIndexed { index, field ->
                        if(field.getAnnotation(PrimaryKey::class)?.autoGenerate == true) {
                            beginControlFlow("if(entity.${field.simpleName.asString()} == %L)",
                                field.type.resolve().defaultTypeValueCode(resolver))
                            add("stmt.setObject(%L, null)\n", index + 1)
                            nextControlFlow("else")
                        }
                        add("stmt.set${field.type.resolve().preparedStatementSetterGetterTypeName(resolver)}(%L, entity.%L)\n",
                            index + 1, field.simpleName.asString())
                        if(field.getAnnotation(PrimaryKey::class)?.autoGenerate == true) {
                            endControlFlow()
                        }
                    }
                }
                .build())
            .build())

    addProperty(PropertySpec.builder(propertyName,
        EntityInsertionAdapter::class.asClassName().parameterizedBy(entityClassName))
        .initializer("%L", insertAdapterSpec.build())
        .build())

    return this
}


class DbProcessorJdbcKotlin: AbstractDbProcessor() {

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
//        val daos = roundEnv.getElementsAnnotatedWith(Dao::class.java)
//
//        for(daoElement in daos) {
//            Napier.d("Processing dao: ${daoElement.simpleName}")
//            val daoTypeEl = daoElement as TypeElement
//            FileSpec.builder(daoElement.packageName,
//                daoElement.simpleName.toString() + SUFFIX_JDBC_KT2)
//                .addDaoJdbcImplType(daoTypeEl)
//                .build()
//                .writeToDirsFromArg(listOf(OPTION_JVM_DIRS, OPTION_JS_OUTPUT))
//        }

        Napier.d("DbProcessJdbcKotlin: process complete")
        return true
    }



//    fun FileSpec.Builder.addDaoJdbcImplType(
//        daoTypeElement: TypeElement
//    ) : FileSpec.Builder{
//        Napier.d("DbProcessorJdbcKotlin: addDaoJdbcImplType: start ${daoTypeElement.simpleName}")
//        addImport("com.ustadmobile.door", "DoorDbType")
//        addType(TypeSpec.classBuilder("${daoTypeElement.simpleName}$SUFFIX_JDBC_KT2")
//            .primaryConstructor(FunSpec.constructorBuilder().addParameter("_db",
//                RoomDatabase::class).build())
//            .addProperty(PropertySpec.builder("_db", RoomDatabase::class).initializer("_db").build())
//            .superclass(daoTypeElement.asClassName())
//            .apply {
//                daoTypeElement.allOverridableMethods(processingEnv).forEach { executableEl ->
//                    when {
//                        executableEl.hasAnnotation(Insert::class.java) ->
//                            addDaoInsertFunction(executableEl, daoTypeElement, this)
//
//                        executableEl.hasAnyAnnotation(Query::class.java, RawQuery::class.java) ->
//                            addDaoQueryFunction(executableEl, daoTypeElement)
//
//                        executableEl.hasAnnotation(Update::class.java) ->
//                            addDaoUpdateFunction(executableEl, daoTypeElement)
//
//                        executableEl.hasAnnotation(Delete::class.java) ->
//                            addDaoDeleteFunction(executableEl, daoTypeElement)
//                    }
//                }
//            }
//            .build())
//
//        Napier.d("DbProcessorJdbcKotlin: addDaoJdbcImplType: finish ${daoTypeElement.simpleName}")
//        return this
//    }

    fun TypeSpec.Builder.addDaoQueryFunction(
        funElement: ExecutableElement,
        daoTypeElement: TypeElement
    ): TypeSpec.Builder {
        Napier.d("DbProcessorJdbcKotlin: addDaoQueryFunction: start ${daoTypeElement.simpleName}#${funElement.simpleName}")
        val funSpec = funElement.asFunSpecConvertedToKotlinTypesForDaoFun(
            daoTypeElement.asType() as DeclaredType, processingEnv).build()

        val queryVarsMap = funSpec.parameters.map { it.name to it.type }.toMap()
        val querySql = funElement.getAnnotation(Query::class.java)?.value
        val postgresQuerySql = funElement.getAnnotation(PostgresQuery::class.java)?.value
        val resultType = funSpec.returnType?.unwrapLiveDataOrDataSourceFactory() ?: UNIT
        val rawQueryParamName = if(funElement.hasAnnotation(RawQuery::class.java))
            funSpec.parameters.first().name
        else
            null

        fun CodeBlock.Builder.addLiveDataImpl(
            liveQueryVarsMap: Map<String, TypeName> = queryVarsMap,
            liveResultType: TypeName = resultType,
            liveSql: String? = querySql,
            liveRawQueryParamName: String? = rawQueryParamName,
            livePostgreSql: String? = postgresQuerySql
        ): CodeBlock.Builder {
            val tablesToWatch = mutableListOf<String>()
            val specifiedLiveTables = funElement.getAnnotation(QueryLiveTables::class.java)
            if(specifiedLiveTables == null) {
                try {
                    val select = CCJSqlParserUtil.parse(liveSql) as Select
                    val tablesNamesFinder = TablesNamesFinder()
                    tablesToWatch.addAll(tablesNamesFinder.getTableList(select))
                }catch(e: Exception) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                        "${makeLogPrefix(funElement.enclosingElement as TypeElement, funElement)}: " +
                                "Sorry: JSQLParser could not parse the query : " +
                                querySql +
                                "Please manually specify the tables to observe using @QueryLiveTables annotation")
                }
            }else {
                tablesToWatch.addAll(specifiedLiveTables.value)
            }

            beginControlFlow("%T<%T>(_db, listOf(%L)) ",
                LiveDataImpl::class.asClassName(),
                liveResultType.copy(nullable = isNullableResultType(liveResultType)),
                tablesToWatch.map {"\"$it\""}.joinToString())
            .addJdbcQueryCode(liveResultType, liveQueryVarsMap, liveSql,
                daoTypeElement, funElement, resultVarName = "_liveResult",
                suspended = true, rawQueryVarName = liveRawQueryParamName,
                querySqlPostgres = livePostgreSql)
            .add("_liveResult")
            .applyIf(liveResultType.isList()) {
                add(".toList()")
            }
            .add("\n")
            .endControlFlow()
            .build()

            Napier.d("DbProcessorJdbcKotlin: addDaoQueryFunction: end ${daoTypeElement.simpleName}#${funElement.simpleName}")
            return this
        }

        addFunction(funSpec.toBuilder()
            .removeAbstractModifier()
            .removeAnnotations()
            .addModifiers(KModifier.OVERRIDE)
            .applyIf(funSpec.returnType?.isDataSourceFactory() == true) {
                val returnTypeUnwrapped = funSpec.returnType?.unwrapQueryResultComponentType()
                    ?: throw IllegalStateException("TODO: datasource not typed - ${daoTypeElement.qualifiedName}#${funSpec.name}")
                addCode("val _result = %L\n",
                TypeSpec.anonymousClassBuilder()
                    .superclass(DataSource.Factory::class.asTypeName().parameterizedBy(INT,
                        returnTypeUnwrapped))
                    .addFunction(FunSpec.builder("getData")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(LiveData::class.asTypeName()
                            .parameterizedBy(List::class.asTypeName().parameterizedBy(returnTypeUnwrapped)))
                        .addParameter("_offset", INT)
                        .addParameter("_limit", INT)
                        .addCode(CodeBlock.builder()
                            .applyIf(rawQueryParamName != null) {
                                add("val _rawQuery = $rawQueryParamName.%M(\n",
                                    MemberName("com.ustadmobile.door.ext", "copyWithExtraParams"))
                                add("sql = \"SELECT * FROM (\${$rawQueryParamName.getSql()}) LIMIT ? OFFSET ?\",\n")
                                add("extraParams = arrayOf(_limit, _offset))\n")
                            }
                            .add("return ")
                            .addLiveDataImpl(
                                liveQueryVarsMap = queryVarsMap + mapOf("_offset" to INT, "_limit" to INT),
                                liveResultType = List::class.asClassName().parameterizedBy(returnTypeUnwrapped),
                                liveSql = querySql?.let { "SELECT * FROM ($it) LIMIT :_limit OFFSET :_offset " },
                                liveRawQueryParamName = rawQueryParamName?.let { "_rawQuery" },
                                livePostgreSql =  postgresQuerySql?.let { "SELECT * FROM ($it) LIMIT :_limit OFFSET :_offset " }
                            )
                            .build())
                        .build())
                    .addFunction(FunSpec.builder("getLength")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(LiveData::class.asTypeName().parameterizedBy(INT))
                        .addCode(CodeBlock.builder()
                            .applyIf(rawQueryParamName != null) {
                                add("val _rawQuery = $rawQueryParamName.%M(\n",
                                    MemberName("com.ustadmobile.door.ext", "copy"))
                                add("sql = \"SELECT COUNT(*) FROM (\${$rawQueryParamName.getSql()})\")\n")
                            }
                            .add("return ")
                            .addLiveDataImpl(
                                liveResultType = INT,
                                liveSql = querySql?.let { "SELECT COUNT(*) FROM ($querySql) "},
                                liveRawQueryParamName = rawQueryParamName?.let { "_rawQuery" },
                                livePostgreSql = postgresQuerySql?.let { "SELECT COUNT(*) FROM ($querySql) "})
                            .build())
                        .build())
                    .build())
            }.applyIf(funSpec.returnType?.isLiveData() == true) {
                addCode(CodeBlock.builder()
                    .add("val _result = ")
                    .addLiveDataImpl()
                    .build())
            }.applyIf(funSpec.returnType?.isDataSourceFactoryOrLiveData() != true) {
                addCode(CodeBlock.builder().addJdbcQueryCode(resultType, queryVarsMap, querySql,
                    daoTypeElement, funElement, rawQueryVarName = rawQueryParamName,
                    suspended = funSpec.isSuspended, querySqlPostgres = postgresQuerySql)
                    .build())
            }
            .applyIf(funSpec.hasReturnType) {
                addCode("return _result\n")
            }
            .build())

        return this
    }


    fun TypeSpec.Builder.addDaoDeleteFunction(
        funElement: ExecutableElement,
        daoTypeElement: TypeElement
    ) : TypeSpec.Builder {
        Napier.d("DbProcessorJdbcKotlin: addDaoDeleteFunction: start ${daoTypeElement.simpleName}#${funElement.simpleName}")
        val funSpec = funElement.asFunSpecConvertedToKotlinTypesForDaoFun(
            daoTypeElement.asType() as DeclaredType, processingEnv).build()
        val entityType = funSpec.parameters.first().type.unwrapListOrArrayComponentType()
        val entityTypeEl = (entityType as ClassName).asTypeElement(processingEnv)
            ?: throw IllegalStateException("Could not resolve ${entityType.canonicalName}")
        val pkEls = entityTypeEl.entityPrimaryKeys
        val stmtSql = "DELETE FROM ${entityTypeEl.simpleName} WHERE " +
                pkEls.joinToString(separator = " AND ") { "${it.simpleName} = ?" }
        val firstParam = funSpec.parameters.first()
        var entityVarName = firstParam.name

        addFunction(funSpec.toBuilder()
            .removeAbstractModifier()
            .removeAnnotations()
            .addModifiers(KModifier.OVERRIDE)
            .addCode(CodeBlock.builder()
                .add("var _numChanges = 0\n")
                .beginControlFlow("_db.%M(%S)", prepareAndUseStatmentMemberName(funSpec.isSuspended),
                    stmtSql)
                .add(" _stmt ->\n")
                .applyIf(firstParam.type.isListOrArray()) {
                    add("_stmt.getConnection().setAutoCommit(false)\n")
                    beginControlFlow("for(_entity in ${firstParam.name})")
                    entityVarName = "_entity"
                }
                .apply {
                    pkEls.forEachIndexed { index, pkEl ->
                        add("_stmt.set${pkEl.asType().asTypeName().preparedStatementSetterGetterTypeName}(%L, %L)\n",
                            index + 1, "$entityVarName.${pkEl.simpleName}")
                    }
                }
                .apply {
                    if(funSpec.isSuspended) {
                        add("_numChanges += _stmt.%M()\n", MEMBERNAME_EXEC_UPDATE_ASYNC)
                    }else {
                        add("_numChanges += _stmt.executeUpdate()\n")
                    }
                }
                .applyIf(firstParam.type.isListOrArray()) {
                    endControlFlow()
                    add("_stmt.getConnection().commit()\n")
                }
                .endControlFlow()
                .applyIf(funSpec.hasReturnType) {
                    add("return _numChanges\n")
                }
                .build())
            .build())

        Napier.d("DbProcessorJdbcKotlin: addDaoDeleteFunction: finish ${daoTypeElement.simpleName}#${funElement.simpleName}")
        return this
    }

    fun TypeSpec.Builder.addDaoUpdateFunction(
        funElement: ExecutableElement,
        daoTypeElement: TypeElement
    ) : TypeSpec.Builder {
        Napier.d("DbProcessorJdbcKotlin: addDaoUpdateFunction: start ${daoTypeElement.simpleName}#${funElement.simpleName}")
        val funSpec = funElement.asFunSpecConvertedToKotlinTypesForDaoFun(
            daoTypeElement.asType() as DeclaredType, processingEnv).build()
        val entityType = funSpec.parameters.first().type.unwrapListOrArrayComponentType()
        val entityTypeEl = (entityType as ClassName).asTypeElement(processingEnv)
            ?: throw IllegalStateException("Could not resolve ${entityType.canonicalName}")
        val pkEls = entityTypeEl.entityPrimaryKeys
        val nonPkFields = entityTypeEl.entityFields.filter {
            it.simpleName !in pkEls.map { it.simpleName }
        }
        val sqlSetPart = nonPkFields.map { "${it.simpleName} = ?" }.joinToString()
        val sqlStmt  = "UPDATE ${entityTypeEl.simpleName} SET $sqlSetPart " +
                "WHERE ${pkEls.joinToString(separator = " AND ") { "${it.simpleName} = ?" }}"
        val firstParam = funSpec.parameters.first()
        var entityVarName = firstParam.name

        addFunction(funSpec.toBuilder()
            .removeAnnotations()
            .removeAbstractModifier()
            .addModifiers(KModifier.OVERRIDE)
            .addCode(CodeBlock.builder()
                .applyIf(funSpec.hasReturnType) {
                    add("var _result = %L\n", funSpec.returnType?.defaultTypeValueCode())
                }
                .add("val _sql = %S\n", sqlStmt)
                .beginControlFlow("_db.%M(_sql)", prepareAndUseStatmentMemberName(funSpec.isSuspended))
                .add(" _stmt ->\n")
                .applyIf(firstParam.type.isListOrArray()) {
                    add("_stmt.getConnection().setAutoCommit(false)\n")
                        .beginControlFlow("for(_entity in ${firstParam.name})")
                    entityVarName = "_entity"
                }
                .apply {
                    var fieldIndex = 1
                    nonPkFields.forEach {
                        add("_stmt.set${it.asType().asTypeName().preparedStatementSetterGetterTypeName}")
                        add("(%L, %L)\n", fieldIndex++, "$entityVarName.${it.simpleName}")
                    }
                    pkEls.forEach { pkEl ->
                        add("_stmt.set${pkEl.asType().asTypeName().preparedStatementSetterGetterTypeName}")
                        add("(%L, %L)\n", fieldIndex++, "$entityVarName.${pkEl.simpleName}")
                    }
                }
                .applyIf(funSpec.hasReturnType) {
                    add("_result += ")
                }
                .applyIf(funSpec.isSuspended) {
                    add("_stmt.%M()\n", MEMBERNAME_EXEC_UPDATE_ASYNC)
                }
                .applyIf(!funSpec.isSuspended) {
                    add("_stmt.executeUpdate()\n")
                }
                .applyIf(firstParam.type.isListOrArray()) {
                    endControlFlow()
                    add("_stmt.getConnection().commit()\n")
                }
                .endControlFlow()
                .applyIf(funSpec.hasReturnType) {
                    add("return _result")
                }
                .build())

            .build())

        Napier.d("DbProcessorJdbcKotlin: addDaoUpdateFunction: finish ${daoTypeElement.simpleName}#${funElement.simpleName}")
        return this
    }





    fun makeLogPrefix(enclosing: TypeElement, method: ExecutableElement) = "DoorDb: ${enclosing.qualifiedName}. ${method.simpleName} "

    companion object {

        //As it should be including the underscore - the above will be deprecated
        const val SUFFIX_JDBC_KT2 = "_JdbcKt"

        const val SUFFIX_REP_RUN_ON_CHANGE_RUNNER = "_ReplicationRunOnChangeRunner"

        const val SUFFIX_JS_IMPLEMENTATION_CLASSES = "JsImplementations"

    }
}

fun FileSpec.Builder.addDaoJdbcImplType(
    daoKSClass: KSClassDeclaration,
    resolver: Resolver,
    target: DoorTarget,
) : FileSpec.Builder{
    Napier.d("DbProcessorJdbcKotlin: addDaoJdbcImplType: start ${daoKSClass.simpleName.asString()}")
    addImport("com.ustadmobile.door", "DoorDbType")
    addType(TypeSpec.classBuilder(daoKSClass.toClassNameWithSuffix(SUFFIX_JDBC_KT2))
        .primaryConstructor(FunSpec.constructorBuilder().addParameter("_db",
            RoomDatabase::class).build())
        .addProperty(PropertySpec.builder("_db", RoomDatabase::class).initializer("_db").build())
        .superclass(daoKSClass.toClassName())
        .apply {
            daoKSClass.getAllFunctions().filter { it.hasAnnotation(Insert::class) }.forEach { daoFun ->
                addDaoInsertFunction(daoFun, daoKSClass, resolver, target)
            }
        }
        .build())

    Napier.d("DbProcessorJdbcKotlin: addDaoJdbcImplType: finish ${daoKSClass.simpleName.asString()}")
    return this
}

fun TypeSpec.Builder.addDaoInsertFunction(
    daoFun: KSFunctionDeclaration,
    daoKSClass: KSClassDeclaration,
    resolver: Resolver,
    target: DoorTarget,
): TypeSpec.Builder {
    Napier.d("Start add dao insert function: ${daoFun.simpleName.asString()} on ${daoKSClass.simpleName.asString()}")

    addFunction(daoFun.toFunSpecBuilder(resolver, daoKSClass.asType(emptyList()))
        .removeAbstractModifier()
        .removeAnnotations()
        .addModifiers(KModifier.OVERRIDE)
        .addCode(CodeBlock.builder()
            .addDaoJdbcInsertCodeBlock(daoFun, daoKSClass, this, resolver, target)
            .build())
        .build())
    Napier.d("Finish dao insert function: ${daoFun.simpleName.asString()} on ${daoKSClass.simpleName.asString()}")
    return this
}



class DoorJdbcProcessor(
    private val environment: SymbolProcessorEnvironment,
): SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val dbSymbols = resolver.getSymbolsWithAnnotation("androidx.room.Database")
            .filterIsInstance<KSClassDeclaration>()

        val daoSymbols = resolver.getSymbolsWithAnnotation("androidx.room.Dao")
            .filterIsInstance<KSClassDeclaration>()

        dbSymbols.forEach {  dbKSClass ->
            JDBC_TARGETS.forEach { target ->
                FileSpec.builder(dbKSClass.packageName.asString(), dbKSClass.simpleName.asString() + SUFFIX_JDBC_KT2)
                    .addJdbcDbImplType(dbKSClass, DoorTarget.JVM, resolver)
                    .build()
                    .writeToPlatformDir(target, environment.codeGenerator, environment.options)
            }
            DoorTarget.values().forEach { target ->
                FileSpec.builder(dbKSClass.packageName.asString(), dbKSClass.simpleName.asString() + SUFFIX_DOOR_METADATA)
                    .addDatabaseMetadataType(dbKSClass)
                    .build()
                    .writeToPlatformDir(target, environment.codeGenerator, environment.options)

                FileSpec.builder(dbKSClass.packageName.asString(), dbKSClass.simpleName.asString() + DbProcessorJdbcKotlin.SUFFIX_REP_RUN_ON_CHANGE_RUNNER)
                    .addReplicationRunOnChangeRunnerType(dbKSClass)
                    .build()
                    .writeToPlatformDir(target, environment.codeGenerator, environment.options)
            }

            FileSpec.builder(dbKSClass.packageName.asString(), dbKSClass.simpleName.asString() + SUFFIX_JS_IMPLEMENTATION_CLASSES)
                .addJsImplementationsClassesObject(dbKSClass)
                .build()
                .writeToPlatformDir(DoorTarget.JS, environment.codeGenerator, environment.options)
        }

        daoSymbols.forEach { daoKSClass ->
            JDBC_TARGETS.forEach { target ->
                FileSpec.builder(daoKSClass.packageName.asString(),
                    daoKSClass.simpleName.asString() + SUFFIX_JDBC_KT2)
                    .addDaoJdbcImplType(daoKSClass, resolver, target)
                    .build()
                    .writeToPlatformDir(target, environment.codeGenerator, environment.options)
            }
        }

        return emptyList()
    }

    companion object {

        val JDBC_TARGETS = listOf(DoorTarget.JVM, DoorTarget.JS)
    }

}
