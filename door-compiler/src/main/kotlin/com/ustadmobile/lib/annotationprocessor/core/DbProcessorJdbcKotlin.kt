package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.*
import com.squareup.kotlinpoet.*
import javax.annotation.processing.*
import javax.lang.model.element.*
import javax.lang.model.type.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.*
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.util.TablesNamesFinder
import org.jetbrains.annotations.Nullable
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import kotlin.reflect.jvm.internal.impl.name.FqName
import kotlin.reflect.jvm.internal.impl.builtins.jvm.JavaToKotlinClassMap
import javax.lang.model.util.SimpleTypeVisitor7
import javax.tools.Diagnostic
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.annotation.*
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorSync.Companion.TRACKER_SUFFIX
import com.ustadmobile.door.ext.DoorDatabaseMetadata
import com.ustadmobile.door.ext.DoorDatabaseMetadata.Companion.SUFFIX_DOOR_METADATA
import com.ustadmobile.door.ext.minifySql
import com.ustadmobile.door.replication.ReplicationRunOnChangeRunner
import com.ustadmobile.door.replication.ReplicationEntityMetaData
import com.ustadmobile.door.replication.ReplicationFieldMetaData
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_ANDROID_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_JS_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_JVM_DIRS
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorRepository.Companion.SUFFIX_REPOSITORY2
import com.ustadmobile.lib.annotationprocessor.core.ext.filterByClass
import com.ustadmobile.lib.annotationprocessor.core.ext.getClassArrayValue
import com.ustadmobile.lib.annotationprocessor.core.ext.getClassValue
import kotlin.reflect.KClass

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



fun pkgNameOfElement(element: Element, processingEnv: ProcessingEnvironment) =
        processingEnv.elementUtils.getPackageOf(element).qualifiedName.toString()

@Suppress("UNCHECKED_CAST")
fun entityTypesOnDb(dbType: TypeElement, processingEnv: ProcessingEnvironment): MutableList<TypeElement> {
    val entityTypeElements = mutableListOf<TypeElement>()
    for (annotationMirror in dbType.getAnnotationMirrors()) {
        val annotationTypeEl = processingEnv.typeUtils
                .asElement(annotationMirror.getAnnotationType()) as TypeElement
        if (annotationTypeEl.qualifiedName.toString() != "androidx.room.Database")
            continue

        val annotationEntryMap = annotationMirror.getElementValues()
        for (entry in annotationEntryMap.entries) {
            val key = entry.key.getSimpleName().toString()
            val value = entry.value.getValue()
            if (key == "entities") {
                val typeMirrors = value as List<AnnotationValue>
                for (entityValue in typeMirrors) {
                    entityTypeElements.add(processingEnv.typeUtils
                            .asElement(entityValue.value as TypeMirror) as TypeElement)
                }
            }
        }
    }


    return entityTypeElements
}


/**
 * Returns a list of the entity fields of a particular object. If getAutoIncLast is true, then
 * any autoincrement primary key will always be returned at the end of the list, e.g. so that a
 * preparedstatement insert with or without an autoincrement id can share the same code to set
 * all other parameters.
 *
 */
fun getEntityFieldElements(entityTypeSpec: TypeSpec,
                           getAutoIncLast: Boolean): List<PropertySpec> {
    val propertyList = entityTypeSpec.propertySpecs.toMutableList()
    if(getAutoIncLast) {
        val autoIncPropIdx = propertyList
                .indexOfFirst { it.annotations.any { it.className == PrimaryKey::class.asClassName()
                        && it.members.any { it.toString().contains("autoGenerate") }} }
        if(autoIncPropIdx >= 0) {
            val autoIncField = propertyList.removeAt(autoIncPropIdx)
            propertyList.add(autoIncField)
        }
    }

    return propertyList
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


fun overrideAndConvertToKotlinTypes(method: ExecutableElement, enclosing: DeclaredType,
                                    processingEnv: ProcessingEnvironment, forceNullableReturn: Boolean = false,
                                    forceNullableParameterTypeArgs: Boolean = false): FunSpec.Builder {

    val funSpec = FunSpec.builder(method.simpleName.toString())
            .addModifiers(KModifier.OVERRIDE)
    val resolvedExecutableType = processingEnv.typeUtils.asMemberOf(enclosing, method) as ExecutableType

    var suspendedReturnType = null as TypeName?
    var suspendedParamEl = null as VariableElement?
    for(i in 0 until method.parameters.size) {
        val resolvedTypeName = resolvedExecutableType.parameterTypes[i].asTypeName().javaToKotlinType()

        if(isContinuationParam(resolvedTypeName)) {
            suspendedParamEl= method.parameters[i]
            suspendedReturnType = resolveReturnTypeIfSuspended(resolvedExecutableType)
            funSpec.addModifiers(KModifier.SUSPEND)
        }else {
            funSpec.addParameter(method.parameters[i].simpleName.toString(),
                    resolvedTypeName.copy(nullable = (method.parameters[i].getAnnotation(Nullable::class.java) != null)))
        }
    }

    if(suspendedReturnType != null && suspendedReturnType != UNIT) {
        funSpec.returns(suspendedReturnType.copy(nullable = forceNullableReturn
                || suspendedParamEl?.getAnnotation(Nullable::class.java) != null))
    }else if(suspendedReturnType == null) {
        var returnType = resolvedExecutableType.returnType.asTypeName().javaToKotlinType()
                .copy(nullable = forceNullableReturn || method.getAnnotation(Nullable::class.java) != null)
        if(forceNullableParameterTypeArgs && returnType is ParameterizedTypeName) {
            returnType = returnType.rawType.parameterizedBy(*returnType.typeArguments.map { it.copy(nullable = true)}.toTypedArray())
        }

        funSpec.returns(returnType)
    }

    return funSpec
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
                && returnTypeName.rawType == DoorLiveData::class.asClassName()) {
            returnTypeName.typeArguments[0]
        }else if(returnTypeName is ParameterizedTypeName
            && returnTypeName.rawType == DoorDataSourceFactory::class.asClassName())
            List::class.asClassName().parameterizedBy(returnTypeName.typeArguments[1])
        else {
            returnTypeName
        }

fun makeInsertAdapterMethodName(
    paramType: TypeName,
    returnType: TypeName,
    async: Boolean = false
): String {
    var methodName = "insert"
    if(paramType is ParameterizedTypeName && paramType.rawType == List::class.asClassName()) {
        methodName += "List"
        if(returnType != UNIT)
            methodName += "AndReturnIds"
    }else {
        if(returnType != UNIT) {
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
fun FileSpec.Builder.addDatabaseMetadataType(dbTypeElement: TypeElement, processingEnv: ProcessingEnvironment): FileSpec.Builder {
    addType(TypeSpec.classBuilder("${dbTypeElement.simpleName}$SUFFIX_DOOR_METADATA")
        .superclass(DoorDatabaseMetadata::class.asClassName().parameterizedBy(dbTypeElement.asClassName()))
        .addProperty(PropertySpec.builder("dbClass", KClass::class.asClassName().parameterizedBy(dbTypeElement.asClassName()))
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return %T::class\n", dbTypeElement)
                .build())
            .build())
        .addProperty(PropertySpec.builder("hasReadOnlyWrapper", Boolean::class)
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return %L\n", dbTypeElement.dbHasReplicateWrapper(processingEnv))
                .build())
            .build())
        .addProperty(PropertySpec.builder("syncableTableIdMap", Map::class.parameterizedBy(String::class, Int::class))
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return TABLE_ID_MAP\n", dbTypeElement.asClassNameWithSuffix(SUFFIX_REPOSITORY2))
                .build())
            .build())
        .addProperty(PropertySpec.builder("replicateEntities", Map::class.parameterizedBy(Int::class, ReplicationEntityMetaData::class))
            .addModifiers(KModifier.OVERRIDE)
            .delegate(
                CodeBlock.builder()
                    .beginControlFlow("lazy(%T.NONE)", LazyThreadSafetyMode::class)
                    .add("mapOf<%T, %T>(\n", INT, ReplicationEntityMetaData::class)
                    .apply {
                        dbTypeElement.allDbEntities(processingEnv)
                            .filter { it.hasAnnotation(ReplicateEntity::class.java)}.forEach { replicateEntity ->
                                add("%L to ", replicateEntity.getAnnotation(ReplicateEntity::class.java).tableId)
                                addReplicateEntityMetaDataCode(replicateEntity, processingEnv)
                                add(",\n")
                        }
                    }
                    .add(")\n")
                    .endControlFlow()
                    .build())
            .build())
        .addType(TypeSpec.companionObjectBuilder()
            .addTableIdMapProperty(dbTypeElement, processingEnv)
            .build())
        .build())

    return this
}

private fun CodeBlock.Builder.addReplicateEntityMetaDataCode(
    entity: TypeElement,
    processingEnv: ProcessingEnvironment
): CodeBlock.Builder {

    fun CodeBlock.Builder.addFieldsCodeBlock(typeEl: TypeElement) : CodeBlock.Builder{
        add("listOf(")
        typeEl.entityFields.forEach {
            add("%T(%S, %L),", ReplicationFieldMetaData::class, it.simpleName,
                it.asType().asTypeName().javaToKotlinType().toSqlTypesInt())
        }
        add(")")
        return this
    }

    val repEntityAnnotation = entity.getAnnotation(ReplicateEntity::class.java)
    val trackerTypeEl = entity.getReplicationTracker(processingEnv)
    add("%T(", ReplicationEntityMetaData::class)
    add("%L, ", repEntityAnnotation.tableId)
    add("%S, ", entity.entityTableName)
    add("%S, ", trackerTypeEl.entityTableName)
    add("%S, ", entity.replicationEntityReceiveViewName)
    add("%S, ", entity.entityPrimaryKey?.simpleName.toString())
    add("%S, ", entity.firstFieldWithAnnotation(ReplicationVersionId::class.java).simpleName)
    add("%S, ", trackerTypeEl.firstFieldWithAnnotation(ReplicationEntityForeignKey::class.java))
    add("%S, ", trackerTypeEl.firstFieldWithAnnotation(ReplicationDestinationNodeId::class.java))
    add("%S, ", trackerTypeEl.firstFieldWithAnnotation(ReplicationVersionId::class.java))
    add("%S, \n", trackerTypeEl.firstFieldWithAnnotation(ReplicationTrackerProcessed::class.java))
    addFieldsCodeBlock(entity).add(",\n")
    addFieldsCodeBlock(trackerTypeEl).add("\n")
    add(")")
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
                codeBlock.add("mutableListOf<%T>()", kotlinType.typeArguments[0])
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

fun isLiveData(typeName: TypeName) = (typeName is ParameterizedTypeName
        && typeName.rawType == DoorLiveData::class.asClassName())

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

fun methodsToImplement(typeElement: TypeElement, enclosing: DeclaredType,
                       processingEnv: ProcessingEnvironment,
                       includeImplementedMethods: Boolean = false) :List<Element> {
    return ancestorsToList(typeElement, processingEnv).flatMap {
        it.enclosedElements.filter {
            it.kind ==  ElementKind.METHOD
                    && (includeImplementedMethods || Modifier.ABSTRACT in it.modifiers) //abstract methods in this class
                    && (Modifier.FINAL !in it.modifiers)
        } + it.interfaces.flatMap {
            processingEnv.typeUtils.asElement(it).enclosedElements.filter { it.kind == ElementKind.METHOD } //methods from the interface
        }
    }.filter {
        includeImplementedMethods || !isMethodImplemented(it as ExecutableElement, typeElement, processingEnv)
    }.distinctBy {
        val signatureParamTypes = (processingEnv.typeUtils.asMemberOf(enclosing, it) as ExecutableType)
                .parameterTypes.filter { ! isContinuationParam(it.asTypeName()) }
        MethodToImplement(it.simpleName.toString(), signatureParamTypes.map { it.asTypeName() })
    }
}

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


class DbProcessorJdbcKotlin: AbstractDbProcessor() {

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val dbs = roundEnv.getElementsAnnotatedWith(Database::class.java)

        for(dbTypeEl in dbs) {
            FileSpec.builder(dbTypeEl.packageName, dbTypeEl.simpleName.toString() + SUFFIX_JDBC_KT2)
                .addJdbcDbImplType(dbTypeEl as TypeElement, DoorTarget.JVM)
                .build()
                .writeToDirsFromArg(listOf(OPTION_JVM_DIRS))

            FileSpec.builder(dbTypeEl.packageName, dbTypeEl.simpleName.toString() + SUFFIX_JDBC_KT2)
                .addJdbcDbImplType(dbTypeEl, DoorTarget.JS)
                .build()
                .writeToDirsFromArg(listOf(OPTION_JS_OUTPUT))

            FileSpec.builder(dbTypeEl.packageName, dbTypeEl.simpleName.toString() + SUFFIX_DOOR_METADATA)
                .addDatabaseMetadataType(dbTypeEl, processingEnv)
                .build()
                .writeToDirsFromArg(listOf(OPTION_JVM_DIRS, OPTION_ANDROID_OUTPUT))

            FileSpec.builder(dbTypeEl.packageName, dbTypeEl.simpleName.toString() + SUFFIX_REP_RUN_ON_CHANGE_RUNNER)
                .addReplicationRunOnChangeRunnerType(dbTypeEl)
                .build()
                .writeToDirsFromArg(listOf(OPTION_JVM_DIRS, OPTION_JS_OUTPUT))
        }


        val daos = roundEnv.getElementsAnnotatedWith(Dao::class.java)

        for(daoElement in daos) {
            val daoTypeEl = daoElement as TypeElement
            FileSpec.builder(daoElement.packageName,
                daoElement.simpleName.toString() + SUFFIX_JDBC_KT2)
                .addDaoJdbcImplType(daoTypeEl)
                .build()
                .writeToDirsFromArg(listOf(OPTION_JVM_DIRS, OPTION_JS_OUTPUT))
        }

        return true
    }

    fun FileSpec.Builder.addDaoJdbcImplType(
        daoTypeElement: TypeElement
    ) : FileSpec.Builder{
        addImport("com.ustadmobile.door", "DoorDbType")
        addType(TypeSpec.classBuilder("${daoTypeElement.simpleName}$SUFFIX_JDBC_KT2")
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("_db",
                DoorDatabase::class).build())
            .addProperty(PropertySpec.builder("_db", DoorDatabase::class).initializer("_db").build())
            .superclass(daoTypeElement.asClassName())
            .apply {
                daoTypeElement.allOverridableMethods(processingEnv).forEach { executableEl ->
                    when {
                        executableEl.hasAnnotation(Insert::class.java) ->
                            addDaoInsertFunction(executableEl, daoTypeElement, this)

                        executableEl.hasAnyAnnotation(Query::class.java, RawQuery::class.java) ->
                            addDaoQueryFunction(executableEl, daoTypeElement)

                        executableEl.hasAnnotation(Update::class.java) ->
                            addDaoUpdateFunction(executableEl, daoTypeElement)

                        executableEl.hasAnnotation(Delete::class.java) ->
                            addDaoDeleteFunction(executableEl, daoTypeElement)
                    }
                }
            }
            .build())

        return this
    }

    fun TypeSpec.Builder.addDaoQueryFunction(
        funElement: ExecutableElement,
        daoTypeElement: TypeElement
    ): TypeSpec.Builder {
        val funSpec = funElement.asFunSpecConvertedToKotlinTypesForDaoFun(
            daoTypeElement.asType() as DeclaredType, processingEnv).build()

        val queryVarsMap = funSpec.parameters.map { it.name to it.type }.toMap()
        val querySql = funElement.getAnnotation(Query::class.java)?.value
        val resultType = funSpec.returnType?.unwrapLiveDataOrDataSourceFactory() ?: UNIT
        val rawQueryParamName = if(funElement.hasAnnotation(RawQuery::class.java))
            funSpec.parameters.first().name
        else
            null

        fun CodeBlock.Builder.addLiveDataImpl(
            liveQueryVarsMap: Map<String, TypeName> = queryVarsMap,
            liveResultType: TypeName = resultType,
            liveSql: String? = querySql,
            liveRawQueryParamName: String? = rawQueryParamName
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
                DoorLiveDataImpl::class.asClassName(),
                liveResultType.copy(nullable = isNullableResultType(liveResultType)),
                tablesToWatch.map {"\"$it\""}.joinToString())
            .addJdbcQueryCode(liveResultType, liveQueryVarsMap, liveSql,
                daoTypeElement, funElement, resultVarName = "_liveResult",
                suspended = true, rawQueryVarName = liveRawQueryParamName)
            .add("_liveResult")
            .applyIf(liveResultType.isList()) {
                add(".toList()")
            }
            .add("\n")
            .endControlFlow()
            .build()

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
                    .superclass(DoorDataSourceFactory::class.asTypeName().parameterizedBy(INT,
                        returnTypeUnwrapped))
                    .addFunction(FunSpec.builder("getData")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(DoorLiveData::class.asTypeName()
                            .parameterizedBy(List::class.asTypeName().parameterizedBy(returnTypeUnwrapped)))
                        .addParameter("_offset", INT)
                        .addParameter("_limit", INT)
                        .addCode(CodeBlock.builder()
                            .applyIf(rawQueryParamName != null) {
                                add("val _rawQuery = $rawQueryParamName.%M(\n",
                                    MemberName("com.ustadmobile.door.ext", "copyWithExtraParams"))
                                add("sql = \"(SELECT * FROM (\${$rawQueryParamName.getSql()}) LIMIT ? OFFSET ?\",\n")
                                add("extraParams = arrayOf(_limit, _offset))\n")
                            }
                            .add("return ")
                            .addLiveDataImpl(
                                liveQueryVarsMap = queryVarsMap + mapOf("_offset" to INT, "_limit" to INT),
                                liveResultType = List::class.asClassName().parameterizedBy(returnTypeUnwrapped),
                                liveSql = querySql?.let { "SELECT * FROM ($it) LIMIT :_limit OFFSET :_offset " },
                                liveRawQueryParamName = rawQueryParamName?.let { "_rawQuery" })
                            .build())
                        .build())
                    .addFunction(FunSpec.builder("getLength")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(DoorLiveData::class.asTypeName().parameterizedBy(INT))
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
                                liveRawQueryParamName = rawQueryParamName?.let { "_rawQuery" })
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
                    suspended = funSpec.isSuspended)
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
                .add("var _con = null as %T?\n", CLASSNAME_CONNECTION)
                .add("var _stmt = null as %T?\n", CLASSNAME_PREPARED_STATEMENT)
                .add("var _numChanges = 0\n")
                .beginControlFlow("try")
                .add("_con = _db.openConnection()\n")
                .add("_stmt = _con.prepareStatement(%S)\n", stmtSql)
                .applyIf(firstParam.type.isListOrArray()) {
                    add("_con.setAutoCommit(false)\n")
                    beginControlFlow("for(_entity in ${firstParam.name})")
                    entityVarName = "_entity"
                }
                .apply {
                    pkEls.forEachIndexed { index, pkEl ->
                        add("_stmt.set${pkEl.asType().asTypeName().preparedStatementSetterGetterTypeName}(%L, %L)\n",
                            index + 1, "$entityVarName.${pkEl.simpleName}")
                    }
                }
                .add("_numChanges += _stmt.executeUpdate()\n")
                .applyIf(firstParam.type.isListOrArray()) {
                    endControlFlow()
                    add("_con.commit()\n")
                    add("_con.setAutoCommit(true)\n")
                }
                .beginControlFlow("if(_numChanges > 0)")
                .add("_db.%M(listOf(%S))\n", MEMBERNAME_HANDLE_TABLES_CHANGED, entityTypeEl.simpleName)
                .endControlFlow()
                .nextControlFlow("catch(_e: %T)", CLASSNAME_SQLEXCEPTION)
                .add("_e.printStackTrace()\n")
                .add("throw %T(_e)\n", CLASSNAME_RUNTIME_EXCEPTION)
                .nextControlFlow("finally")
                .add("_stmt?.close()\n")
                .add("_con?.close()\n")
                .endControlFlow()
                .applyIf(funSpec.hasReturnType) {
                    add("return _numChanges\n")
                }
                .build())
            .build())

        return this
    }

    fun TypeSpec.Builder.addDaoUpdateFunction(
        funElement: ExecutableElement,
        daoTypeElement: TypeElement
    ) : TypeSpec.Builder {
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
                .apply {
                    endControlFlow()
                    add("_db.%M(listOf(%S))\n", MEMBERNAME_HANDLE_TABLES_CHANGED, entityTypeEl.simpleName)
                }
                .applyIf(funSpec.hasReturnType) {
                    add("return _result")
                }
                .build())

            .build())
        return this
    }

    fun TypeSpec.Builder.addDaoInsertFunction(
        funElement: ExecutableElement,
        daoTypeElement: TypeElement,
        daoTypeBuilder: TypeSpec.Builder
    ): TypeSpec.Builder {
        val funSpec = funElement.asFunSpecConvertedToKotlinTypesForDaoFun(
            daoTypeElement.asType() as DeclaredType, processingEnv).build()
        val entityType = funSpec.parameters.first().type.unwrapListOrArrayComponentType() as ClassName
        val upsertMode = funElement.getAnnotation(Insert::class.java).onConflict == OnConflictStrategy.REPLACE
        val pgOnConflict = funElement.getAnnotation(PgOnConflict::class.java)?.value
        val entityTypeSpec = entityType.asEntityTypeSpec(processingEnv)
            ?: throw IllegalStateException("Could not resolve ${entityType.canonicalName} to entity type spec")


        addFunction(funSpec.toBuilder()
            .removeAbstractModifier()
            .removeAnnotations()
            .addModifiers(KModifier.OVERRIDE)
            .addCode(CodeBlock.builder()
                .addDaoJdbcInsertCodeBlock(funSpec.parameters.first(),
                funSpec.returnType ?: UNIT,
                    entityTypeSpec,
                    daoTypeBuilder,
                    upsertMode,
                    pgOnConflict = pgOnConflict,
                    suspended = funSpec.isSuspended)
                .build())
            .build())
        return this
    }

    /**
     * Genetes an EntityInsertAdapter for use with JDBC code
     */
    fun TypeSpec.Builder.addDaoJdbcEntityInsertAdapter(
        entityTypeSpec: TypeSpec,
        entityClassName: ClassName,
        propertyName: String,
        upsertMode: Boolean,
        processingEnv: ProcessingEnvironment,
        supportedDbTypes: List<Int>,
        pgOnConflict: String? = null
    ) : TypeSpec.Builder {
        val entityFields = entityTypeSpec.entityFields(getAutoIncLast = false)
        val pkFields = entityClassName.asTypeElement(processingEnv)?.entityPrimaryKeys?.map {
                PropertySpec.builder(it.simpleName.toString(), it.asType().asTypeName()).build()
            } ?: listOf(entityFields.first { it.annotations.any { it.typeName == PrimaryKey::class.asClassName() } })
        val insertAdapterSpec = TypeSpec.anonymousClassBuilder()
            .superclass(EntityInsertionAdapter::class.asClassName().parameterizedBy(entityClassName))
            .addSuperclassConstructorParameter("_db")
            .addFunction(FunSpec.builder("makeSql")
                .addParameter("returnsId", BOOLEAN)
                .addModifiers(KModifier.OVERRIDE)
                .addCode(CodeBlock.builder()
                    .apply {
                        if(supportedDbTypes.size != 1) {
                            beginControlFlow("return when(dbType)")
                        }else{
                            add("return ")
                        }
                    }
                    .applyIf(supportedDbTypes.size != 1 && DoorDbType.SQLITE in supportedDbTypes) {
                        beginControlFlow("%T.SQLITE ->", DoorDbType::class)
                    }
                    .applyIf(DoorDbType.SQLITE in supportedDbTypes) {
                        var insertSql = "INSERT "
                        if(upsertMode)
                            insertSql += "OR REPLACE "
                        insertSql += "INTO ${entityTypeSpec.name} (${entityFields.joinToString { it.name }}) "
                        insertSql += "VALUES(${entityFields.joinToString { "?" }})"
                        add("%S", insertSql)
                    }
                    .applyIf(supportedDbTypes.size != 1 && DoorDbType.SQLITE in supportedDbTypes) {
                        add("\n")
                        endControlFlow()
                    }
                    .applyIf(supportedDbTypes.size != 1 && DoorDbType.POSTGRES in supportedDbTypes) {
                        beginControlFlow("%T.POSTGRES -> ", DoorDbType::class)
                    }
                    .applyIf(DoorDbType.POSTGRES in supportedDbTypes) {
                        var insertSql = "INSERT "
                        insertSql += "INTO ${entityTypeSpec.name} (${entityFields.joinToString { it.name }}) "
                        insertSql += "VALUES("
                        insertSql += entityFields.joinToString { prop ->
                            if(prop.annotations.any { it.typeName == PrimaryKey::class.asClassName() &&
                                        it.members.findBooleanMemberValue("autoGenerate") == true }) {
                                "COALESCE(?,nextval('${entityTypeSpec.name}_${prop.name}_seq'))"
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
                                insertSql += " ON CONFLICT (${pkFields.joinToString()}) DO UPDATE SET "
                                insertSql += entityFields.filter { !it.annotations.any { it.typeName == PrimaryKey::class.asTypeName() } }
                                    .joinToString(separator = ",") {
                                        "${it.name} = excluded.${it.name}"
                                    }
                            }
                        }
                        add("%S·+·if(returnsId)·{·%S·}·else·\"\"·", insertSql, " RETURNING ${pkFields.first().name}")
                    }
                    .applyIf(supportedDbTypes.size != 1 && DoorDbType.POSTGRES in supportedDbTypes) {
                        add("\n")
                        endControlFlow()
                    }
                    .apply {
                        if(supportedDbTypes.size != 1) {
                            beginControlFlow("else ->")
                            add("throw %T(%S)\n", CLASSNAME_ILLEGALARGUMENTEXCEPTION, "Unsupported db type")
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
                .addParameter("stmt", CLASSNAME_PREPARED_STATEMENT)
                .addParameter("entity", entityClassName)
                .addCode(CodeBlock.builder()
                    .apply {
                        entityFields.forEachIndexed { index, field ->
                            if(field.isEntityAutoGenPrimaryKey) {
                                beginControlFlow("if(entity.${field.name} == %L)",
                                    field.type.defaultTypeValueCode())
                                add("stmt.setObject(%L, null)\n", index + 1)
                                nextControlFlow("else")
                            }
                            add("stmt.set${field.type.preparedStatementSetterGetterTypeName}(%L, entity.%L)\n",
                                index + 1, field.name)
                            if(field.isEntityAutoGenPrimaryKey) {
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

    fun CodeBlock.Builder.addDaoJdbcInsertCodeBlock(
        parameterSpec: ParameterSpec,
        returnType: TypeName,
        entityTypeSpec: TypeSpec,
        daoTypeBuilder: TypeSpec.Builder,
        upsertMode: Boolean = false,
        addReturnStmt: Boolean = true,
        pgOnConflict: String? = null,
        supportedDbTypes: List<Int> = DoorDbType.SUPPORTED_TYPES,
        suspended: Boolean = false
    ): CodeBlock.Builder {
        val paramType = parameterSpec.type
        val entityClassName = paramType.unwrapListOrArrayComponentType() as ClassName

        val pgOnConflictHash = pgOnConflict?.hashCode()?.let { Math.abs(it) }?.toString() ?: ""
        val entityInserterPropName = "_insertAdapter${entityTypeSpec.name}_${if(upsertMode) "upsert" else ""}$pgOnConflictHash"
        if(!daoTypeBuilder.propertySpecs.any { it.name == entityInserterPropName }) {
            daoTypeBuilder.addDaoJdbcEntityInsertAdapter(entityTypeSpec, entityClassName, entityInserterPropName,
                upsertMode, processingEnv, supportedDbTypes, pgOnConflict)
        }

        if(returnType != UNIT) {
            add("val _retVal = ")
        }


        val insertMethodName = makeInsertAdapterMethodName(paramType, returnType, suspended)
        add("$entityInserterPropName.$insertMethodName(${parameterSpec.name})")

        if(returnType != UNIT) {
            if(returnType.isListOrArray()
                && returnType is ParameterizedTypeName
                && returnType.typeArguments[0] == INT) {
                add(".map { it.toInt() }")
            }else if(returnType == INT){
                add(".toInt()")
            }
        }

        add("\n")

        add("_db.%M(listOf(%S))\n", MEMBERNAME_HANDLE_TABLES_CHANGED, entityTypeSpec.name)

        if(addReturnStmt) {
            if(returnType != UNIT) {
                add("return _retVal")
            }

            if(returnType is ParameterizedTypeName
                && returnType.rawType == ARRAY) {
                add(".toTypedArray()")
            }else if(returnType == LongArray::class.asClassName()) {
                add(".toLongArray()")
            }else if(returnType == IntArray::class.asClassName()) {
                add(".toIntArray()")
            }
        }

        return this
    }

    fun FileSpec.Builder.addReplicationRunOnChangeRunnerType(
        dbTypeElement: TypeElement
    ): FileSpec.Builder {
        //find all DAOs on the database that contain a ReplicationRunOnChange annotation
        val daosWithRunOnChange = dbTypeElement.dbEnclosedDaos(processingEnv)
            .filter { it.enclosedElementsWithAnnotation(ReplicationRunOnChange::class.java).isNotEmpty() }
        val daosWithRunOnNewNode = dbTypeElement.dbEnclosedDaos(processingEnv)
            .filter { it.enclosedElementsWithAnnotation(ReplicationRunOnNewNode::class.java).isNotEmpty() }

        val replicatedEntitiesWithOnChangeFunctions = daosWithRunOnChange
            .flatMap {
                val funsWithOnChange = it.enclosedElementsWithAnnotation(ReplicationRunOnChange::class.java)
                val repOnChangeAnnotationMirrors = funsWithOnChange.flatMap {
                    it.annotationMirrors.filterByClass(processingEnv, ReplicationRunOnChange::class)
                }
                repOnChangeAnnotationMirrors.flatMap { it.getClassArrayValue("value", processingEnv) }
            }


        addType(TypeSpec.classBuilder(dbTypeElement.asClassNameWithSuffix(SUFFIX_REP_RUN_ON_CHANGE_RUNNER))
            .addSuperinterface(ReplicationRunOnChangeRunner::class)
            .addAnnotation(AnnotationSpec.builder(Suppress::class)
                .addMember("%S, %S, %S, %S", "LocalVariableName", "RedundantVisibilityModifier", "unused", "ClassName")
                .build())
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("_db", dbTypeElement.asClassName(), KModifier.PRIVATE)
                .build())
            .addProperty(PropertySpec.builder("_db", dbTypeElement.asClassName())
                .initializer("_db")
                .build())
            .apply {
                //TODO: this should have an abstract super class to implement the MessageBus
                replicatedEntitiesWithOnChangeFunctions.forEach { repEntity ->
                    addFunction(FunSpec.builder("handle${repEntity.simpleName}Changed")
                        .receiver(dbTypeElement.asClassName())
                        .addModifiers(KModifier.SUSPEND)
                        .addModifiers(KModifier.PRIVATE)
                        .returns(Set::class.parameterizedBy(String::class))
                        .addCode(CodeBlock.builder()
                            .apply {
                                val repTablesToCheck = mutableSetOf<String>()
                                daosWithRunOnChange.forEach { dao ->
                                    val daoFunsToRun = dao.enclosedElements
                                        .filter { it.hasAnyAnnotation { it.getClassValue("value", processingEnv) == repEntity} }
                                    val daoAccessorCodeBlock = dbTypeElement.findDaoGetter(dao, processingEnv)
                                    daoFunsToRun.mapNotNull { it as? ExecutableElement}.forEach { funToRun ->
                                        add("%L.${funToRun.simpleName}(", daoAccessorCodeBlock)
                                        if(funToRun.parameters.first().hasAnnotation(NewNodeIdParam::class.java)) {
                                            add("0L")
                                        }

                                        add(")\n")
                                        val checkingPendingRepTableNames = funToRun.annotationMirrors
                                            .firstOrNull() { it.annotationType.asElement() == ReplicationCheckPendingNotificationsFor::class.asTypeElement(processingEnv) }
                                            ?.getClassArrayValue("value", processingEnv)
                                            ?.map { it.simpleName.toString() }  ?: listOf(repEntity.simpleName.toString())
                                        repTablesToCheck.addAll(checkingPendingRepTableNames)
                                    }
                                }
                                add("%M(%L)\n",
                                    MemberName("com.ustadmobile.door.ext", "deleteFromChangeLog"),
                                    repEntity.getAnnotation(ReplicateEntity::class.java).tableId)

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
                            dbTypeElement)
                        add("_transactionDb ->\n")
                        beginControlFlow("when")
                        replicatedEntitiesWithOnChangeFunctions.forEach { repEntity ->
                            beginControlFlow("%S in tableNames -> ", repEntity.simpleName)
                            add("_checkPendingNotifications.addAll(_transactionDb.handle${repEntity.simpleName}Changed())\n")
                            endControlFlow()
                        }
                        endControlFlow()
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
                            dbTypeElement)
                        add("_transactionDb -> \n")
                        daosWithRunOnNewNode.forEach { dao ->
                            val daoAccessorCodeBlock = dbTypeElement.findDaoGetter(dao, processingEnv)
                            dao.enclosedElementsWithAnnotation(ReplicationRunOnNewNode::class.java, ElementKind.METHOD).forEach { daoFun ->
                                add("_transactionDb.%L.${daoFun.simpleName}(newNodeId)\n", daoAccessorCodeBlock)
                                val funEntitiesChanged = daoFun.annotationMirrors
                                    .firstOrNull() { it.annotationType.asElement() == ReplicationCheckPendingNotificationsFor::class.asTypeElement(processingEnv) }
                                    ?.getClassArrayValue("value", processingEnv)  ?: listOf()

                                entitiesChanged += funEntitiesChanged.map { it.entityTableName }
                            }
                        }
                        endControlFlow()
                        add("return setOf(${entitiesChanged.joinToString(separator = "\",\"", prefix = "\"", postfix = "\"")})\n")
                    }
                    .build())
                .build())
            .build())

        return this
    }


    fun FileSpec.Builder.addJdbcDbImplType(
        dbTypeElement: TypeElement,
        target: DoorTarget
    ) : FileSpec.Builder {
        addImport("com.ustadmobile.door.util", "systemTimeInMillis")
        addType(TypeSpec.classBuilder(dbTypeElement.asClassNameWithSuffix(SUFFIX_JDBC_KT2))
            .superclass(dbTypeElement.asClassName())
            .addSuperinterface(DoorDatabaseJdbc::class)
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("doorJdbcSourceDatabase", DoorDatabase::class.asTypeName().copy(nullable = true),
                    KModifier.OVERRIDE)
                .addParameter(ParameterSpec("dataSource",  CLASSNAME_DATASOURCE,
                    KModifier.OVERRIDE))
                .applyIf(dbTypeElement.isDbSyncable(processingEnv)) {
                    addParameter(ParameterSpec.builder("master", BOOLEAN)
                        .defaultValue("false")
                        .addModifiers(KModifier.OVERRIDE).build())
                }
                .addParameter("dbName", String::class, KModifier.OVERRIDE)
                .addCode("setupFromDataSource()\n")
                .build())
            .addDbVersionProperty(dbTypeElement)
            .addProperty(PropertySpec.builder("dataSource", CLASSNAME_DATASOURCE)
                .initializer("dataSource")
                .build())
            .addProperty(PropertySpec.builder("doorJdbcSourceDatabase",
                    DoorDatabase::class.asTypeName().copy(nullable = true))
                .initializer("doorJdbcSourceDatabase")
                .build())
            .addProperty(PropertySpec.builder("dbName", String::class)
                .initializer("dbName")
                .build())
            .applyIf(target == DoorTarget.JVM) {
                addProperty(PropertySpec.builder("isInTransaction", Boolean::class,
                    KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder()
                        .addCode("return currentTransactionDepth > 0\n")
                        .build())
                    .build())
            }
            .applyIf(target == DoorTarget.JS) {
                addProperty(PropertySpec.builder("isInTransaction", Boolean::class,
                    KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder()
                        .addCode("return false\n")
                        .build())
                    .build())
            }
            .applyIf(dbTypeElement.isDbSyncable(processingEnv)) {
                addProperty(PropertySpec.builder("master", BOOLEAN)
                    .initializer("master").build())
            }
            .addProperty(PropertySpec.builder("realPrimaryKeyManager", DoorPrimaryKeyManager::class,
                    KModifier.OVERRIDE)
                .delegate(CodeBlock.builder()
                    .beginControlFlow("lazy")
                    .beginControlFlow("if(isInTransaction)")
                    .add("throw %T(%S)\n", ClassName("kotlin", "IllegalStateException"),
                        "doorPrimaryKeyManager must be used on root database ONLY, not transaction wrapper!")
                    .endControlFlow()
                    .add("%T(%T::class.%M().replicateEntities.keys)\n", DoorPrimaryKeyManager::class,
                        dbTypeElement, MemberName("com.ustadmobile.door.ext", "doorDatabaseMetadata"))
                    .endControlFlow()
                    .build())
                .build())
            .addCreateAllTablesFunction(dbTypeElement)
            .addClearAllTablesFunction(dbTypeElement)
            .apply {
                val daoGetters = dbTypeElement.enclosedElements
                    .filter { it.kind == ElementKind.METHOD }
                    .mapNotNull { it as? ExecutableElement }
                    .filter { it.modifiers.contains(Modifier.ABSTRACT) }

                daoGetters.forEach {
                    val daoTypeEl = processingEnv.typeUtils.asElement(it.returnType) as TypeElement
                    val daoImplClassName = daoTypeEl.asClassNameWithSuffix(SUFFIX_JDBC_KT2)
                    addProperty(PropertySpec.builder("_${daoTypeEl.simpleName}",
                        daoImplClassName).delegate("lazy·{·%T(this)·}", daoImplClassName).build())
                    addAccessorOverride(it, CodeBlock.of("return _${daoTypeEl.simpleName}"))
                }

            }
            .build())
        return this
    }

    fun TypeSpec.Builder.addCreateAllTablesFunction(
        dbTypeElement: TypeElement
    ) : TypeSpec.Builder {
        val initDbVersion = dbTypeElement.getAnnotation(Database::class.java).version
        addFunction(FunSpec.builder("createAllTables")
            .addModifiers(KModifier.OVERRIDE)
            .returns(List::class.parameterizedBy(String::class))
            .addCode(CodeBlock.builder()
                .add("val _stmtList = %M<String>()\n", MEMBERNAME_MUTABLE_LINKEDLISTOF)
                .beginControlFlow("when(jdbcDbType)")
                .apply {
                    for(dbProductType in DoorDbType.SUPPORTED_TYPES) {
                        val dbTypeName = DoorDbType.PRODUCT_INT_TO_NAME_MAP[dbProductType] as String
                        beginControlFlow("$dbProductType -> ")
                        add("// - create for this $dbTypeName \n")
                        add("_stmtList += \"CREATE·TABLE·IF·NOT·EXISTS·${DoorDatabaseCommon.DBINFO_TABLENAME}" +
                                "·(dbVersion·int·primary·key,·dbHash·varchar(255))\"\n")
                        add(" _stmtList += \"INSERT·INTO·${DoorDatabaseCommon.DBINFO_TABLENAME}·" +
                                "VALUES·($initDbVersion,·'')\"\n")

                        //All entities MUST be created first, triggers etc. can only be created after all entities exist
                        val createEntitiesCodeBlock = CodeBlock.builder()
                        val createTriggersAndViewsBlock = CodeBlock.builder()

                        dbTypeElement.allDbEntities(processingEnv).forEach { entityType ->
                            val fieldListStr = entityType.entityFields.joinToString { it.simpleName.toString() }
                            createEntitiesCodeBlock.add("//Begin: Create table ${entityType.simpleName} for $dbTypeName\n")
                                .add("/* START MIGRATION: \n")
                                .add("_stmt.executeUpdate(%S)\n",
                                    "ALTER TABLE ${entityType.simpleName} RENAME to ${entityType.simpleName}_OLD")
                                .add("END MIGRATION */\n")
                                .addCreateTableCode(entityType.asEntityTypeSpec(), entityType.packageName,
                                    "_stmt.executeUpdate", dbProductType, processingEnv,
                                    entityType.indicesAsIndexMirrorList(), sqlListVar = "_stmtList")
                                .add("/* START MIGRATION: \n")
                                .add("_stmt.executeUpdate(%S)\n", "INSERT INTO ${entityType.simpleName} ($fieldListStr) " +
                                    "SELECT $fieldListStr FROM ${entityType.simpleName}_OLD")
                                .add("_stmt.executeUpdate(%S)\n", "DROP TABLE ${entityType.simpleName}_OLD")
                                .add("END MIGRATION*/\n")

                            if(entityType.hasAnnotation(SyncableEntity::class.java)) {
                                val syncableEntityInfo = SyncableEntityInfo(entityType.asClassName(),
                                    processingEnv)
                                createTriggersAndViewsBlock.addSyncableEntityTriggers(entityType.asClassName(),
                                    "_stmt.executeUpdate", dbProductType, sqlListVar = "_stmtList")

                                if(dbProductType == DoorDbType.POSTGRES) {
                                    createTriggersAndViewsBlock.add("/* START MIGRATION: \n")
                                    createTriggersAndViewsBlock.add("_stmt.executeUpdate(%S)\n",
                                        "DROP FUNCTION IF EXISTS inc_csn_${syncableEntityInfo.tableId}_fn")
                                    createTriggersAndViewsBlock.add("_stmt.executeUpdate(%S)\n",
                                        "DROP SEQUENCE IF EXISTS spk_seq_${syncableEntityInfo.tableId}")
                                    createTriggersAndViewsBlock.add("END MIGRATION*/\n")
                                }

                                val trackerEntityClassName = generateTrackerEntity(entityType, processingEnv)
                                createEntitiesCodeBlock.add("_stmtList += %S\n", makeCreateTableStatement(
                                    trackerEntityClassName, dbProductType, entityType.packageName))

                                createEntitiesCodeBlock.add(generateCreateIndicesCodeBlock(
                                    arrayOf(IndexMirror(value = arrayOf(DbProcessorSync.TRACKER_DESTID_FIELDNAME,
                                        DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME,
                                        DbProcessorSync.TRACKER_CHANGESEQNUM_FIELDNAME)),
                                        IndexMirror(value = arrayOf(DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME,
                                            DbProcessorSync.TRACKER_DESTID_FIELDNAME), unique = true)),
                                    trackerEntityClassName.name!!, "_stmtList"))
                            }

                            if(entityType.hasAnnotation(ReplicateEntity::class.java)) {
                                createTriggersAndViewsBlock
                                    .addReplicateEntityChangeLogTrigger(entityType, "_stmtList", dbProductType)
                                    .addCreateReceiveView(entityType, "_stmtList")
                            }

                            createTriggersAndViewsBlock.addCreateTriggersCode(entityType, "_stmtList",
                                dbProductType)


                            if(entityType.entityHasAttachments) {
                                if(dbProductType == DoorDbType.SQLITE) {
                                    createTriggersAndViewsBlock.addGenerateAttachmentTriggerSqlite(entityType,
                                        "_stmt.executeUpdate", "_stmtList")
                                }else {
                                    createTriggersAndViewsBlock.addGenerateAttachmentTriggerPostgres(entityType, "_stmtList")
                                }
                            }

                            createEntitiesCodeBlock.add("//End: Create table ${entityType.simpleName} for $dbTypeName\n\n")
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

        return this
    }


    /**
     * Add a ReceiveView for the given EntityTypeElement.
     */
    private fun CodeBlock.Builder.addCreateReceiveView(
        entityTypeEl: TypeElement,
        sqlListVar: String
    ): CodeBlock.Builder {
        val trkrEl = entityTypeEl.getReplicationTracker(processingEnv)
        val receiveViewAnn = entityTypeEl.getAnnotation(ReplicateReceiveView::class.java)
        val viewName = receiveViewAnn?.name ?: "${entityTypeEl.entityTableName}$SUFFIX_DEFAULT_RECEIVEVIEW"
        val sql = receiveViewAnn?.value ?: """
            SELECT ${entityTypeEl.simpleName}.*, ${trkrEl.entityTableName}.*
              FROM ${entityTypeEl.simpleName}
                   LEFT JOIN ${trkrEl.simpleName} ON ${trkrEl.entityTableName}.${trkrEl.replicationTrackerForeignKey.simpleName} = 
                        ${entityTypeEl.entityTableName}.${entityTypeEl.entityPrimaryKey?.simpleName}
        """.minifySql()
        add("$sqlListVar += %S\n", "CREATE VIEW $viewName AS $sql")
        return this
    }

    fun TypeSpec.Builder.addClearAllTablesFunction(
        dbTypeElement: TypeElement
    ) : TypeSpec.Builder {
        addFunction(FunSpec.builder("clearAllTables")
            .addModifiers(KModifier.OVERRIDE)
            .addCode("var _con = null as %T?\n", CLASSNAME_CONNECTION)
            .addCode("var _stmt = null as %T?\n", CLASSNAME_STATEMENT)
            .beginControlFlow("try")
            .addCode("_con = openConnection()!!\n")
            .addCode("_stmt = _con.createStatement()!!\n")
            .apply {
                dbTypeElement.allDbEntities(processingEnv).forEach {  entityType ->
                    addCode("_stmt.executeUpdate(%S)\n", "DELETE FROM ${entityType.simpleName}")
                    if(entityType.hasAnnotation(SyncableEntity::class.java)) {
                        addCode("_stmt.executeUpdate(%S)\n",
                            "DELETE FROM ${entityType.simpleName}$TRACKER_SUFFIX")
                    }
                }
            }
            .nextControlFlow("finally")
            .addCode("_stmt?.close()\n")
            .addCode("_con?.close()\n")
            .endControlFlow()
            .build())
        return this
    }

    fun makeLogPrefix(enclosing: TypeElement, method: ExecutableElement) = "DoorDb: ${enclosing.qualifiedName}. ${method.simpleName} "

    companion object {

        const val SUFFIX_JDBC_KT = "JdbcKt"

        //As it should be including the underscore - the above will be deprecated
        const val SUFFIX_JDBC_KT2 = "_JdbcKt"

        const val SUFFIX_REP_RUN_ON_CHANGE_RUNNER = "_ReplicationRunOnChangeRunner"


    }
}