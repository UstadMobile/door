package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.*
import com.squareup.kotlinpoet.*
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.*
import javax.sql.DataSource
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.*
import com.ustadmobile.door.annotation.SyncableEntity
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.util.TablesNamesFinder
import org.jetbrains.annotations.Nullable
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import kotlin.reflect.jvm.internal.impl.name.FqName
import kotlin.reflect.jvm.internal.impl.builtins.jvm.JavaToKotlinClassMap
import org.sqlite.SQLiteDataSource
import java.sql.*
import java.util.Locale
import javax.lang.model.util.SimpleTypeVisitor7
import javax.tools.Diagnostic
import com.ustadmobile.door.SyncableDoorDatabase
import kotlin.math.absoluteValue
import kotlin.random.Random
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.annotation.QueryLiveTables
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorSync.Companion.TRACKER_SUFFIX
import kotlin.RuntimeException
import com.ustadmobile.door.annotation.PgOnConflict

val QUERY_SINGULAR_TYPES = listOf(INT, LONG, SHORT, BYTE, BOOLEAN, FLOAT, DOUBLE,
        String::class.asTypeName(), String::class.asTypeName().copy(nullable = true))


fun isList(type: TypeMirror, processingEnv: ProcessingEnvironment): Boolean =
        type.kind == TypeKind.DECLARED && (processingEnv.typeUtils.asElement(type) as TypeElement).qualifiedName.toString() == "java.util.List"



fun entityTypeFromFirstParam(method: ExecutableElement, enclosing: DeclaredType, processingEnv: ProcessingEnvironment) : TypeMirror {
    val methodResolved = processingEnv.typeUtils.asMemberOf(enclosing, method) as ExecutableType
    if(methodResolved.parameterTypes.isEmpty()) {
        return processingEnv.typeUtils.nullType
    }

    val firstParamType = methodResolved.parameterTypes[0]
    if(isList(firstParamType, processingEnv)) {
        val firstType = (firstParamType as DeclaredType).typeArguments[0]
        return if(firstType is WildcardType) {
            firstType.extendsBound
        }else {
            firstType
        }
    }else if(firstParamType.kind == TypeKind.ARRAY) {
        return (firstParamType as ArrayType).componentType
    }else {
        return firstParamType
    }
}


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
 * @param entityTypeElement The TypeElement representing the entity, from which we wish to get
 * the field names
 * @param getAutoIncLast if true, then always return any field that is auto increment at the very end
 * @return List of VariableElement representing the entity fields that are persisted
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

internal fun containsAutoGeneratePk(annotationList: List<AnnotationSpec>) =
        annotationList.any {
            it.className == PrimaryKey::class.asClassName()
            && it.members.map { it.toString().trim() }
            .any { it.startsWith("autoGenerate") && it.endsWith("true")}
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
                && returnTypeName.rawType == androidx.paging.DataSource.Factory::class.asClassName()) {
            List::class.asClassName().parameterizedBy(returnTypeName.typeArguments[1])
        }else {
            returnTypeName
        }

fun makeInsertAdapterMethodName(paramType: TypeName, returnType: TypeName, processingEnv: ProcessingEnvironment): String {
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

    return methodName
}

fun getPreparedStatementSetterGetterTypeName(typeName: TypeName): String? {
    val kotlinType = typeName.javaToKotlinType()
    when(kotlinType) {
        INT -> return "Int"
        BYTE -> return "Byte"
        LONG -> return "Long"
        FLOAT -> return "Float"
        DOUBLE -> return "Double"
        BOOLEAN -> return "Boolean"
        String::class.asTypeName() -> return "String"
        String::class.asTypeName().copy(nullable = true) -> return "String"
        else -> {
            if(kotlinType.isListOrArray()) {
                return "Array"
            }else {
                return "UNKNOWN"
            }
        }
    }
}

/**
 * For SQL with named parameters (e.g. "SELECT * FROM Table WHERE uid = :paramName") return a
 * list of all named parameters.
 *
 * @param querySql SQL that may contain named parameters
 * @return String list of named parameters (e.g. "paramName"). Empty if no named parameters are present.
 */
fun getQueryNamedParameters(querySql: String): List<String> {
    val namedParams = mutableListOf<String>()
    var insideQuote = false
    var insideDoubleQuote = false
    var lastC: Char = 0.toChar()
    var startNamedParam = -1
    for (i in 0 until querySql.length) {
        val c = querySql[i]
        if (c == '\'' && lastC != '\\')
            insideQuote = !insideQuote
        if (c == '\"' && lastC != '\\')
            insideDoubleQuote = !insideDoubleQuote

        if (!insideQuote && !insideDoubleQuote) {
            if (c == ':') {
                startNamedParam = i
            } else if (!(Character.isLetterOrDigit(c) || c == '_') && startNamedParam != -1) {
                //process the parameter
                namedParams.add(querySql.substring(startNamedParam + 1, i))
                startNamedParam = -1
            } else if (i == querySql.length - 1 && startNamedParam != -1) {
                namedParams.add(querySql.substring(startNamedParam + 1, i + 1))
                startNamedParam = -1
            }
        }


        lastC = c
    }

    return namedParams
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
            val getterName = "get${getPreparedStatementSetterGetterTypeName(it.type) }"
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

fun TypeName.isArrayType(): Boolean = (this is ParameterizedTypeName && this.rawType.canonicalName == "kotlin.Array")

fun isDataSourceFactory(typeName: TypeName) = typeName is ParameterizedTypeName
        && typeName.rawType == androidx.paging.DataSource.Factory::class.asTypeName()


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

fun fieldsOnEntity(entityType: TypeElement) = entityType.enclosedElements.filter {
    it.kind  == ElementKind.FIELD && it.simpleName.toString() != "Companion"
            && !it.modifiers.contains(Modifier.STATIC)
}

internal val masterDbVals = mapOf(DoorDbType.SQLITE to listOf("0", "1"),
        DoorDbType.POSTGRES to listOf("false", "true"))

internal fun generateInsertNodeIdFun(dbType: TypeElement, jdbcDbType: Int,
                                     execSqlFunName: String = "_stmt.executeUpdate",
                                     processingEnv: ProcessingEnvironment,
                                     isUpdate: Boolean = false): CodeBlock {
    val codeBlock = CodeBlock.builder()
    codeBlock.add("val _nodeId = %T.nextInt(1, %T.MAX_VALUE)\n",
            Random::class, Int::class)
            .add("println(\"Setting SyncNode nodeClientId = \$_nodeId\")\n")
            .add("$execSqlFunName(\"INSERT·INTO·SyncNode(nodeClientId,master)·VALUES")

    when(jdbcDbType) {
        DoorDbType.SQLITE -> codeBlock.add("·(\$_nodeId,\${if(master)·1·else·0})")
        DoorDbType.POSTGRES -> codeBlock.add("·(\$_nodeId,\$master)")
    }

    codeBlock.add("\")\n")


    syncableEntityTypesOnDb(dbType, processingEnv).forEach {
        val syncableEntityInfo = SyncableEntityInfo(it.asClassName(), processingEnv)
        if(jdbcDbType == DoorDbType.SQLITE) {
            if(isUpdate) {
                codeBlock.add("$execSqlFunName(%S)\n",
                        "UPDATE sqlite_sequence SET seq = ((SELECT nodeClientId FROM SyncNode) << 32) WHERE name = '${it.simpleName}'")
            }else {
                codeBlock.add("$execSqlFunName(%S)\n",
                        "INSERT OR REPLACE INTO sqlite_sequence(name,seq) VALUES('${it.simpleName}', ((SELECT nodeClientId FROM SyncNode) << 32)) ")
            }
            codeBlock.addReplaceSqliteChangeSeqNums(execSqlFunName, syncableEntityInfo)
        }else if(jdbcDbType == DoorDbType.POSTGRES){
            codeBlock.add("$execSqlFunName(\"ALTER·SEQUENCE·" +
                    "${it.simpleName}_${syncableEntityInfo.entityPkField.name}_seq·RESTART·WITH·\${_nodeId·shl·32}\")\n")
        }
    }

    return codeBlock.build()
}

/**
 * Generate insert statements that will put the TableSyncStatus entities required for each
 * syncable entity on the database in place.
 */
internal fun generateInsertTableSyncStatusCodeBlock(dbType: TypeElement,
                                         execSqlFunName: String = "_stmt.executeUpdate",
                                         processingEnv: ProcessingEnvironment): CodeBlock {
    val codeBlock = CodeBlock.builder()
    syncableEntityTypesOnDb(dbType, processingEnv).forEach {
        val syncableEntityInfo = SyncableEntityInfo(it.asClassName(), processingEnv)
        codeBlock.add("$execSqlFunName(\"INSERT·INTO·TableSyncStatus(tsTableId,·tsLastChanged,·tsLastSynced)·" +
                    "VALUES(${syncableEntityInfo.tableId},·\${systemTimeInMillis()},·0)\")\n")
    }

    return codeBlock.build()
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
            val dbFileSpec = generateDbImplClass(dbTypeEl as TypeElement)
            writeFileSpecToOutputDirs(dbFileSpec, AnnotationProcessorWrapper.OPTION_JVM_DIRS)
        }


        val daos = roundEnv.getElementsAnnotatedWith(Dao::class.java)

        for(daoElement in daos) {
            val daoTypeEl = daoElement as TypeElement
            val daoFileSpec = generateDaoImplClass(daoTypeEl)
            writeFileSpecToOutputDirs(daoFileSpec, AnnotationProcessorWrapper.OPTION_JVM_DIRS)
        }

        return true
    }


    fun generateDaoImplClass(daoTypeElement: TypeElement): FileSpec {
        val daoImplFile = FileSpec.builder(pkgNameOfElement(daoTypeElement, processingEnv),
                "${daoTypeElement.simpleName}_$SUFFIX_JDBC_KT")
        daoImplFile.addImport("com.ustadmobile.door", "DoorDbType")
        val daoImpl = jdbcDaoTypeSpecBuilder("${daoTypeElement.simpleName}_$SUFFIX_JDBC_KT",
                daoTypeElement.asClassName())

        methodsToImplement(daoTypeElement, daoTypeElement.asType() as DeclaredType, processingEnv).forEach {daoSubEl ->
            if(daoSubEl.kind != ElementKind.METHOD)
                return@forEach

            val daoMethod = daoSubEl as ExecutableElement
            if(daoMethod.getAnnotation(Insert::class.java) != null) {
                daoImpl.addFunction(generateInsertFun(daoTypeElement, daoMethod, daoImpl))
            }else if(daoMethod.getAnnotation(Query::class.java) != null
                    || daoMethod.getAnnotation(RawQuery::class.java) != null) {
                daoImpl.addFunction(generateQueryFun(daoTypeElement, daoMethod, daoImpl))
            }else if(daoMethod.getAnnotation(Update::class.java) != null) {
                daoImpl.addFunction(generateUpdateFun(daoTypeElement, daoMethod, daoImpl))
            }else if(daoMethod.getAnnotation(Delete::class.java) != null) {
                daoImpl.addFunction(generateDeleteFun(daoTypeElement, daoMethod))
            }else {
                messager?.printMessage(Diagnostic.Kind.ERROR,
                        "${makeLogPrefix(daoTypeElement, daoMethod)}: Abstract method on DAO not annotated with Query, RawQuery, Update, Delete, or Insert",
                        daoMethod)
            }
        }

        daoImplFile.addType(daoImpl.build())
        return daoImplFile.build()
    }


    fun generateDbImplClass(dbTypeElement: TypeElement): FileSpec {
        val dbImplFile = FileSpec.builder(pkgNameOfElement(dbTypeElement, processingEnv),
                "${dbTypeElement.simpleName}_$SUFFIX_JDBC_KT")
                .addImport("com.ustadmobile.door.util", "systemTimeInMillis")


        val constructorFn = FunSpec.constructorBuilder()
                .addParameter("dataSource", DataSource::class)
                .addCode("this.dataSource = dataSource\n")
                .addCode("setupFromDataSource()\n")
        val dbImplType = TypeSpec.classBuilder("${dbTypeElement.simpleName}_$SUFFIX_JDBC_KT")
                .superclass(dbTypeElement.asClassName())
                .addDbVersionProperty(dbTypeElement)

        if(isSyncableDb(dbTypeElement, processingEnv)) {
            constructorFn.addParameter(ParameterSpec.builder("master", BOOLEAN)
                    .defaultValue("false")
                    .addModifiers(KModifier.OVERRIDE).build())
            dbImplType.addProperty(PropertySpec.builder("master", BOOLEAN)
                    .initializer("master").build())
        }


        dbImplType.primaryConstructor(constructorFn.build())
        dbImplType.addFunction(generateCreateTablesFun(dbTypeElement))
        dbImplType.addFunction(generateClearAllTablesFun(dbTypeElement))

        for(subEl in dbTypeElement.enclosedElements) {
            if(subEl.kind != ElementKind.METHOD)
                continue

            val methodEl = subEl as ExecutableElement
            val daoTypeEl = processingEnv.typeUtils.asElement(methodEl.returnType)
            if(!methodEl.modifiers.contains(Modifier.ABSTRACT))
                continue

            val daoImplClassName = ClassName(pkgNameOfElement(daoTypeEl, processingEnv),
                    "${daoTypeEl.simpleName}_$SUFFIX_JDBC_KT")

            dbImplType.addProperty(PropertySpec.builder("_${daoTypeEl.simpleName}",
                    daoImplClassName).delegate("lazy { %T(this) }", daoImplClassName).build())

            if(subEl.simpleName.startsWith("get")) {
                //must be overriden using a val
                val propName = subEl.simpleName.substring(3, 4).toLowerCase(Locale.ROOT) + subEl.simpleName.substring(4)
                val getterFunSpec = FunSpec.getterBuilder().addStatement("return _${daoTypeEl.simpleName}").build()
                dbImplType.addProperty(PropertySpec.builder(propName,
                        methodEl.returnType.asTypeName(), KModifier.OVERRIDE)
                        .getter(getterFunSpec).build())
            }else {
                dbImplType.addFunction(FunSpec.overriding(methodEl)
                        .addStatement("return _${daoTypeEl.simpleName}").build())
            }

        }

        writeMigrationTemplates(dbTypeElement)
        dbImplFile.addType(dbImplType.build())

        return dbImplFile.build()
    }

    fun generateCreateTablesFun(dbTypeElement: TypeElement): FunSpec {
        val createTablesFunSpec = FunSpec.builder("createAllTables")
                .addModifiers(KModifier.OVERRIDE)
        val initDbVersion = dbTypeElement.getAnnotation(Database::class.java).version
        val codeBlock = CodeBlock.builder()
        codeBlock.add("var _con = null as %T?\n", Connection::class)
                .add("var _stmt = null as %T?\n", Statement::class)
                .beginControlFlow("try")
                .add("_con = openConnection()!!\n")
                .add("_stmt = _con.createStatement()!!\n")
                .beginControlFlow("when(jdbcDbType)")

        val dbTypeIsSyncable = processingEnv.typeUtils.isAssignable(dbTypeElement.asType(),
                processingEnv.elementUtils.getTypeElement(
                        SyncableDoorDatabase::class.java.canonicalName).asType())



        for(dbProductType in DoorDbType.SUPPORTED_TYPES) {
            val dbTypeName = DoorDbType.PRODUCT_INT_TO_NAME_MAP[dbProductType] as String
            codeBlock.beginControlFlow("$dbProductType -> ")
                    .add("// - create for this $dbTypeName \n")
            codeBlock.add("_stmt.executeUpdate(\"CREATE·TABLE·IF·NOT·EXISTS·${DoorDatabase.DBINFO_TABLENAME}" +
                    "·(dbVersion·int·primary·key,·dbHash·varchar(255))\")\n")
            codeBlock.add("_stmt.executeUpdate(\"INSERT·INTO·${DoorDatabase.DBINFO_TABLENAME}·" +
                    "VALUES·($initDbVersion,·'')\")\n")

            val dbEntityTypes = entityTypesOnDb(dbTypeElement, processingEnv)
            for(entityType in dbEntityTypes) {
                codeBlock.add("//Begin: Create table ${entityType.simpleName} for $dbTypeName\n")
                val entityTypeSpec = entityType.asEntityTypeSpec()
                val fieldElements = getEntityFieldElements(entityTypeSpec, false)
                val fieldListStr = fieldElements.joinToString { it.name }
                codeBlock.add("/* START MIGRATION: \n")
                        .add("_stmt.executeUpdate(%S)\n", "ALTER TABLE ${entityTypeSpec.name} RENAME to ${entityTypeSpec.name}_OLD")
                        .add("END MIGRATION */\n")

                codeBlock.addCreateTableCode(entityTypeSpec, "_stmt.executeUpdate",
                    dbProductType, entityType.indicesAsIndexMirrorList())
                codeBlock.add("/* START MIGRATION: \n")
                        .add("_stmt.executeUpdate(%S)\n", "INSERT INTO ${entityTypeSpec.name} ($fieldListStr) SELECT $fieldListStr FROM ${entityTypeSpec.name}_OLD")
                        .add("_stmt.executeUpdate(%S)\n", "DROP TABLE ${entityTypeSpec.name}_OLD")
                        .add("END MIGRATION*/\n")

                if(entityType.getAnnotation(SyncableEntity::class.java) != null) {
                    val syncableEntityInfo = SyncableEntityInfo(entityType.asClassName(),
                            processingEnv)

                    codeBlock.add(generateSyncTriggersCodeBlock(entityType.asClassName(),
                            "_stmt.executeUpdate", dbProductType))
                    if(dbProductType == DoorDbType.POSTGRES) {
                        codeBlock.add("/* START MIGRATION: \n")
                                .add("_stmt.executeUpdate(%S)\n", "DROP FUNCTION IF EXISTS inc_csn_${syncableEntityInfo.tableId}_fn")
                                .add("_stmt.executeUpdate(%S)\n", "DROP SEQUENCE IF EXISTS spk_seq_${syncableEntityInfo.tableId}")
                                .add("END MIGRATION*/\n")
                    }


                    val trackerEntityClassName = generateTrackerEntity(entityType, processingEnv)
                    codeBlock.add("_stmt.executeUpdate(%S)\n", makeCreateTableStatement(
                            trackerEntityClassName, dbProductType))
                    codeBlock.add(generateCreateIndicesCodeBlock(
                            arrayOf(IndexMirror(value = arrayOf(DbProcessorSync.TRACKER_DESTID_FIELDNAME,
                                    DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME,
                                    DbProcessorSync.TRACKER_CHANGESEQNUM_FIELDNAME)),
                                    IndexMirror(value = arrayOf(DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME,
                                        DbProcessorSync.TRACKER_DESTID_FIELDNAME), unique = true)),
                            trackerEntityClassName.name!!, "_stmt.executeUpdate"))
                }

                if(entityType.entityHasAttachments) {
                    if(dbProductType == DoorDbType.SQLITE) {
                        codeBlock.addGenerateAttachmentTriggerSqlite(entityType, "_stmt.executeUpdate")
                    }else {
                        codeBlock.addGenerateAttachmentTriggerPostgres(entityType, "_stmt.executeUpdate")
                    }
                }

                codeBlock.add("//End: Create table ${entityType.simpleName} for $dbTypeName\n\n")
            }

            if(dbTypeIsSyncable){
                codeBlock.add(generateInsertNodeIdFun(dbTypeElement, dbProductType, "_stmt.executeUpdate",
                        processingEnv))
            }

            codeBlock.endControlFlow()
        }


        codeBlock.endControlFlow() //end when
                .apply {
                    takeIf { dbTypeIsSyncable }?.addInsertTableSyncStatuses(dbTypeElement,
                            "_stmt.executeUpdate", processingEnv)
                }.nextControlFlow("catch(e: %T)", Exception::class)
                .add("e.printStackTrace()\n")
                .add("throw %T(%S, e)\n", RuntimeException::class, "Exception creating tables")
                .nextControlFlow("finally")
                .add("_stmt?.close()\n")
                .add("_con?.close()\n")
                .endControlFlow()
        return createTablesFunSpec.addCode(codeBlock.build()).build()
    }


    fun writeMigrationTemplates(dbTypeElement: TypeElement) {
        val fixSqliteUpdateTriggerDest = processingEnv.options[ARG_MIGRATION_TEMPLATE_SQLITE_UPDATE_TRIGGER]
        if(fixSqliteUpdateTriggerDest != null) {
            val codeBlock = CodeBlock.builder()
            entityTypesOnDb(dbTypeElement, processingEnv)
                    .filter { it.getAnnotation(SyncableEntity::class.java) != null }
                    .forEach {entityClass ->
                        val entityClassName = entityClass.asClassName()
                        val syncableEntityInfo = SyncableEntityInfo(entityClassName, processingEnv)
                        codeBlock.add("database.execSQL(\"DROP TRIGGER IF EXISTS UPD_${syncableEntityInfo.tableId}\")\n")
                            .add("database.execSQL(\"DROP TRIGGER IF EXISTS INS_${syncableEntityInfo.tableId}\")\n")
                                .add(generateSyncTriggersCodeBlock(entityClassName, "database.execSQL",
                                        DoorDbType.SQLITE))
                    }

            val destFile = File(fixSqliteUpdateTriggerDest)
            if(!destFile.parentFile.exists()) {
                destFile.parentFile.mkdirs()
            }


            FileSpec.builder(dbTypeElement.asClassName().packageName, "FixSqliteTrigger")
                    .addType(TypeSpec.classBuilder("FixSqliteTrigger")
                            .addFunction(FunSpec.builder("doFix")
                                    .addCode(codeBlock.build())
                                    .build())
                            .build())
                    .build().writeTo(File(fixSqliteUpdateTriggerDest))

        }
    }

    fun generateClearAllTablesFun(dbTypeElement: TypeElement): FunSpec {
        val dropFunSpec = FunSpec.builder("clearAllTables")
                .addModifiers(KModifier.OVERRIDE)
                .addCode("var _con = null as %T?\n", Connection::class)
                .addCode("var _stmt = null as %T?\n", Statement::class)
                .beginControlFlow("try")
                .addCode("_con = openConnection()\n")
                .addCode("_stmt = _con!!.createStatement()\n")
        for(entityType in entityTypesOnDb(dbTypeElement, processingEnv)) {
            dropFunSpec.addCode("_stmt!!.executeUpdate(%S)\n", "DELETE FROM ${entityType.simpleName}")
            if(entityType.getAnnotation(SyncableEntity::class.java) != null){
                dropFunSpec.addCode("_stmt!!.executeUpdate(%S)\n", "DELETE FROM ${entityType.simpleName}$TRACKER_SUFFIX")
            }
        }

        dropFunSpec.beginControlFlow("when(jdbcDbType)")
        DoorDbType.SUPPORTED_TYPES.forEach {
            dropFunSpec.beginControlFlow("$it -> ")
                    .addCode(generateInsertNodeIdFun(dbTypeElement, it, "_stmt.executeUpdate", processingEnv,
                            isUpdate = true))
                    .endControlFlow()
        }
        dropFunSpec.endControlFlow()
        dropFunSpec.addCode(CodeBlock.builder().addInsertTableSyncStatuses(dbTypeElement,
                "_stmt.executeUpdate",processingEnv).build())

        dropFunSpec.nextControlFlow("finally")
                .addCode("_stmt?.close()\n")
                .addCode("_con?.close()\n")
                .endControlFlow()

        return dropFunSpec.build()
    }



    fun generateInsertFun(daoTypeElement: TypeElement, daoMethod: ExecutableElement, daoTypeBuilder: TypeSpec.Builder): FunSpec {
        val insertFun = overrideAndConvertToKotlinTypes(daoMethod, daoTypeElement.asType() as DeclaredType,
                processingEnv)

        val daoMethodResolved = processingEnv.typeUtils.asMemberOf(daoTypeElement.asType() as DeclaredType,
                daoMethod) as ExecutableType


        val entityType = entityTypeFromFirstParam(daoMethod, daoTypeElement.asType() as DeclaredType,
                processingEnv)

        if(entityType == processingEnv.typeUtils.nullType) {
            logMessage(Diagnostic.Kind.ERROR, "Insert function first parameter must be an entity object",
                    daoTypeElement, daoMethod)
            return insertFun.build()
        }

        val entityTypeEl = processingEnv.typeUtils.asElement(entityType) as TypeElement

        if(entityTypeEl.getAnnotation(Entity::class.java) == null) {
            logMessage(Diagnostic.Kind.ERROR, "Insert method entity type must be annotated @Entity",
                    daoTypeElement, daoMethod)
            return insertFun.build()
        }

        val upsertMode = daoMethod.getAnnotation(Insert::class.java).onConflict == OnConflictStrategy.REPLACE
        val resolvedReturnType = resolveReturnTypeIfSuspended(daoMethodResolved).javaToKotlinType()
        insertFun.addCode(generateInsertCodeBlock(
                insertFun.parameters[0],
                resolvedReturnType, entityTypeEl.asEntityTypeSpec(),
                daoTypeBuilder, upsertMode,
                pgOnConflict = daoMethod.getAnnotation(PgOnConflict::class.java)?.value))
        return insertFun.build()
    }


    fun generateQueryFun(daoTypeElement: TypeElement, daoMethod: ExecutableElement, daoTypeBuilder: TypeSpec.Builder,
                         isRawQuery: Boolean = false) : FunSpec {
        val daoMethodResolved = processingEnv.typeUtils.asMemberOf(daoTypeElement.asType() as DeclaredType,
                daoMethod) as ExecutableType

        // The return type of the method - e.g. List<Entity>, LiveData<List<Entity>>, String, etc.
        val returnTypeResolved = resolveReturnTypeIfSuspended(daoMethodResolved).javaToKotlinType()

        //The type of result with any wrapper (e.g. LiveData) removed e..g List<Entity>, Entity, String, etc.
        val resultType = resolveQueryResultType(returnTypeResolved)

        val funSpec = overrideAndConvertToKotlinTypes(daoMethod, daoTypeElement.asType() as DeclaredType,
                processingEnv,
                forceNullableReturn = isNullableResultType(returnTypeResolved),
                forceNullableParameterTypeArgs = isLiveData(returnTypeResolved)
                        && isNullableResultType((returnTypeResolved as ParameterizedTypeName).typeArguments[0]))

        val querySql = daoMethod.getAnnotation(Query::class.java)?.value

        if(querySql != null
                && !querySql.trim().startsWith("UPDATE", ignoreCase = true)
                && !querySql.trim().startsWith("DELETE", ignoreCase = true)
                && resultType == UNIT) {
            logMessage(Diagnostic.Kind.ERROR, "Query method running SELECT must have a return type")
            return funSpec.build()
        }

        val paramTypesResolved = daoMethodResolved.parameterTypes


        //Perhaps this could be replaced with a bit of mapIndexed + filters
        val queryVarsMap = mutableMapOf<String, TypeName>()
        for(i in 0 until daoMethod.parameters.size) {
            if (!isContinuationParam(paramTypesResolved[i].asTypeName())) {
                queryVarsMap[daoMethod.parameters[i].simpleName.toString()] = paramTypesResolved[i].asTypeName()
            }
        }


        if(isDataSourceFactory(returnTypeResolved)) {
            funSpec.addCode("val _result = %T<%T, %T>()\n",
                    DoorDataSourceJdbc.Factory::class, INT,
                    (returnTypeResolved as ParameterizedTypeName).typeArguments[1])
        }else if(isLiveData(returnTypeResolved)) {
            val tablesToWatch = mutableListOf<String>()
            val specifiedLiveTables = daoMethod.getAnnotation(QueryLiveTables::class.java)
            if(specifiedLiveTables == null) {
                try {
                    val select = CCJSqlParserUtil.parse(querySql) as Select
                    val tablesNamesFinder = TablesNamesFinder()
                    tablesToWatch.addAll(tablesNamesFinder.getTableList(select))
                }catch(e: Exception) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "${makeLogPrefix(daoTypeElement, daoMethod)}: " +
                                    "Sorry: JSQLParser could not parse the query : " +
                                    querySql +
                                    "Please manually specify the tables to observe using @QueryLiveTables annotation")
                }
            }else {
                tablesToWatch.addAll(specifiedLiveTables.value)
            }


            val liveDataCodeBlock = CodeBlock.builder()
                    .beginControlFlow("val _result = %T<%T>(_db, listOf(%L)) ",
                            DoorLiveDataJdbcImpl::class.asClassName(),
                            resultType.copy(nullable = isNullableResultType(resultType)),
                            tablesToWatch.map {"\"$it\""}.joinToString())
                    .add(generateQueryCodeBlock(returnTypeResolved, queryVarsMap, querySql,
                            daoTypeElement, daoMethod, resultVarName = "_liveResult"))
                    .add("_liveResult")

            if(resultType is ParameterizedTypeName && resultType.rawType == List::class.asClassName())
                liveDataCodeBlock.add(".toList()")

            liveDataCodeBlock.add("\n")
                    .endControlFlow()

            funSpec.addCode(liveDataCodeBlock.build())
        }else {
            val rawQueryVarName = if(daoMethod.getAnnotation(RawQuery::class.java) !=null ) {
                daoMethod.parameters[0].simpleName.toString()
            }else {
                null
            }

            funSpec.addCode(generateQueryCodeBlock(returnTypeResolved, queryVarsMap, querySql,
                    daoTypeElement, daoMethod, rawQueryVarName = rawQueryVarName))
        }

        if(returnTypeResolved != UNIT){
            funSpec.addCode("return _result\n")
        }

        return funSpec.build()
    }

    fun generateUpdateFun(daoTypeElement: TypeElement, daoMethod: ExecutableElement, daoTypeBuilder: TypeSpec.Builder) : FunSpec {
        val updateFun = overrideAndConvertToKotlinTypes(daoMethod, daoTypeElement.asType() as DeclaredType,
                processingEnv)

        val daoMethodResolved = processingEnv.typeUtils.asMemberOf(daoTypeElement.asType() as DeclaredType,
                daoMethod) as ExecutableType

        //The parameter type - could be singular (e.g. Entity), could be list/array (e.g. List<Entity>)
        val paramType = daoMethodResolved.parameterTypes[0].asTypeName().javaToKotlinType()

        val entityType = entityTypeFromFirstParam(daoMethod, daoTypeElement.asType() as DeclaredType,
                processingEnv)

        val entityTypeEl = processingEnv.typeUtils.asElement(entityType) as TypeElement

        val resolvedReturnType = resolveReturnTypeIfSuspended(daoMethodResolved)

        val codeBlock = CodeBlock.builder()

        val pkEl = entityTypeEl.enclosedElements.first { it.getAnnotation(PrimaryKey::class.java) != null }
        val nonPkFields = fieldsOnEntity(entityTypeEl).filter { it.kind == ElementKind.FIELD && it.getAnnotation(PrimaryKey::class.java) == null }
        val sqlSetPart = nonPkFields.map { "${it.simpleName} = ?" }.joinToString()
        val sqlStmt  = "UPDATE ${entityTypeEl.simpleName} SET $sqlSetPart WHERE ${pkEl.simpleName} = ?"


        if(resolvedReturnType != UNIT)
            codeBlock.add("var _result = ${defaultVal(resolvedReturnType)}\n")

        codeBlock.add("var _con = null as %T?\n", Connection::class)
                .add("var _stmt = null as %T?\n", PreparedStatement::class)
                .beginControlFlow("try")
                .add("_con = _db.openConnection()!!\n")
                .add("_stmt = _con.prepareStatement(%S)!!\n", sqlStmt)

        var entityVarName = daoMethod.parameters[0].simpleName.toString()
        if(paramType.isListOrArray()) {
            codeBlock.add("_con.autoCommit = false\n")
                    .beginControlFlow("for(_entity in ${daoMethod.parameters[0].simpleName})")
            entityVarName = "_entity"
        }

        var fieldIndex = 1
        val fieldSetFn = { it : Element ->
            codeBlock.add("_stmt.set${getPreparedStatementSetterGetterTypeName(it.asType().asTypeName())}(${fieldIndex++}, $entityVarName.${it.simpleName})\n")
            Unit
        }
        nonPkFields.forEach(fieldSetFn)
        fieldSetFn(pkEl)

        if(resolvedReturnType != UNIT)
            codeBlock.add("_result += ")

        codeBlock.add("_stmt.executeUpdate()\n")

        if(paramType.isListOrArray()) {
            codeBlock.endControlFlow()
                .add("_con.commit()\n")
        }

        codeBlock.nextControlFlow("catch(_e: %T)", SQLException::class)
                .add("_e.printStackTrace()\n")
                .add("throw %T(_e)\n", RuntimeException::class)
                .nextControlFlow("finally")
                .add("_stmt?.close()\n")
                .add("_con?.close()\n")
                .endControlFlow()
                .add("_db.handleTableChanged(listOf(%S))\n", entityTypeEl.simpleName)

        if(resolvedReturnType != UNIT)
            codeBlock.add("return _result\n")

        updateFun.addCode(codeBlock.build())
        return updateFun.build()
    }


    fun generateDeleteFun(daoTypeElement: TypeElement, daoMethod: ExecutableElement): FunSpec {
        val deleteFun = overrideAndConvertToKotlinTypes(daoMethod, daoTypeElement.asType() as DeclaredType,
                processingEnv)

        val daoMethodResolved = processingEnv.typeUtils.asMemberOf(daoTypeElement.asType() as DeclaredType,
                daoMethod) as ExecutableType

        //The parameter type - could be singular (e.g. Entity), could be list/array (e.g. List<Entity>)
        val paramType = daoMethodResolved.parameterTypes[0].asTypeName().javaToKotlinType()

        val entityType = entityTypeFromFirstParam(daoMethod, daoTypeElement.asType() as DeclaredType,
                processingEnv)

        val entityTypeEl = processingEnv.typeUtils.asElement(entityType) as TypeElement

        val resolvedReturnType = resolveReturnTypeIfSuspended(daoMethodResolved)

        val codeBlock = CodeBlock.builder()

        val pkEl = entityTypeEl.enclosedElements.first { it.getAnnotation(PrimaryKey::class.java) != null }

        val stmtSql = "DELETE FROM ${entityTypeEl.simpleName} WHERE ${pkEl.simpleName} = ?"

        codeBlock.add("var _con = null as %T?\n", Connection::class)
                .add("var _stmt = null as %T?\n", PreparedStatement::class)
                .add("var _numChanges = 0\n")
                .beginControlFlow("try")
                .add("_con = _db.openConnection()\n")
                .add("_stmt = _con.prepareStatement(%S)\n", stmtSql)



        var entityVarName = daoMethod.parameters[0].simpleName.toString()
        if(paramType.isListOrArray()) {
            codeBlock.add("_con.autoCommit = false\n")
                    .beginControlFlow("for(_entity in ${daoMethod.parameters[0].simpleName})")
            entityVarName = "_entity"
        }

        codeBlock.add("_stmt.set${getPreparedStatementSetterGetterTypeName(pkEl.asType().asTypeName())}(1, $entityVarName.${pkEl.simpleName})\n")
        codeBlock.add("_numChanges += _stmt.executeUpdate()\n")

        if(paramType.isListOrArray()) {
            codeBlock.endControlFlow()
                .add("_con.commit()\n")
                .add("_con.autoCommit = true\n")
        }

        codeBlock.beginControlFlow("if(_numChanges > 0)")
                .add("_db.handleTableChanged(listOf(%S))\n", entityTypeEl.simpleName)
                .endControlFlow()

        codeBlock.nextControlFlow("catch(_e: %T)", SQLException::class)
                .add("_e.printStackTrace()\n")
                .add("throw %T(_e)\n", RuntimeException::class)
                .nextControlFlow("finally")
                .add("_stmt?.close()\n")
                .add("_con?.close()\n")
                .endControlFlow()


        if(resolvedReturnType != UNIT)
            codeBlock.add("return _numChanges")

        return deleteFun.addCode(codeBlock.build()).build()
    }

    fun makeLogPrefix(enclosing: TypeElement, method: ExecutableElement) = "DoorDb: ${enclosing.qualifiedName}. ${method.simpleName} "

    companion object {

        const val SUFFIX_JDBC_KT = "JdbcKt"

        //As it should be including the underscore - the above will be deprecated
        const val SUFFIX_JDBC_KT2 = "_JdbcKt"

        const val ARG_MIGRATION_TEMPLATE_SQLITE_UPDATE_TRIGGER = "doordb_template_fixupdatetrigger_sqlite"

    }
}