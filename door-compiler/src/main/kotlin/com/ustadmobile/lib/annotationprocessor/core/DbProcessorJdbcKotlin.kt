package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.*
import com.squareup.kotlinpoet.*
import java.io.File
import javax.annotation.processing.*
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
import javax.lang.model.type.TypeMirror
import kotlin.reflect.jvm.internal.impl.name.FqName
import kotlin.reflect.jvm.internal.impl.builtins.jvm.JavaToKotlinClassMap
import java.sql.*
import java.util.Locale
import javax.lang.model.util.SimpleTypeVisitor7
import javax.tools.Diagnostic
import com.ustadmobile.door.SyncableDoorDatabase
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.annotation.QueryLiveTables
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorSync.Companion.TRACKER_SUFFIX
import kotlin.RuntimeException
import com.ustadmobile.door.annotation.PgOnConflict
import com.ustadmobile.door.ext.DoorDatabaseMetadata
import com.ustadmobile.door.ext.DoorDatabaseMetadata.Companion.SUFFIX_DOOR_METADATA
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_ANDROID_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_JVM_DIRS
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorRepository.Companion.SUFFIX_REPOSITORY2
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
        .addProperty(PropertySpec.builder("syncableTableIdMap", Map::class.parameterizedBy(String::class, Int::class))
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode("return TABLE_ID_MAP\n", dbTypeElement.asClassNameWithSuffix(SUFFIX_REPOSITORY2))
                .build())
            .build())
        .addType(TypeSpec.companionObjectBuilder()
            .addTableIdMapProperty(dbTypeElement, processingEnv)
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

        addFunction(funSpec.toBuilder()
            .removeAbstractModifier()
            .removeAnnotations()
            .addModifiers(KModifier.OVERRIDE)
            .applyIf(funSpec.returnType?.isDataSourceFactory() == true) {
                addCode("val _result = %T<%T, %T>()\n", DoorDataSourceJdbc.Factory::class, INT,
                    funSpec.returnType?.unwrapQueryResultComponentType())
            }.applyIf(funSpec.returnType?.isLiveData() == true) {
                val tablesToWatch = mutableListOf<String>()
                val specifiedLiveTables = funElement.getAnnotation(QueryLiveTables::class.java)
                if(specifiedLiveTables == null) {
                    try {
                        val select = CCJSqlParserUtil.parse(querySql) as Select
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

                addCode(CodeBlock.builder()
                    .beginControlFlow("val _result = %T<%T>(_db, listOf(%L)) ",
                        DoorLiveDataJdbcImpl::class.asClassName(),
                        resultType.copy(nullable = isNullableResultType(resultType)),
                        tablesToWatch.map {"\"$it\""}.joinToString())
                    .add(generateQueryCodeBlock(resultType, queryVarsMap, querySql,
                        daoTypeElement, funElement, resultVarName = "_liveResult"))
                    .add("_liveResult")
                    .applyIf(resultType.isList()) {
                        add(".toList()")
                    }
                    .add("\n")
                    .endControlFlow()
                    .build()
                )
            }.applyIf(funSpec.returnType?.isDataSourceFactoryOrLiveData() != true) {
                val rawQueryParamName = if(funElement.hasAnnotation(RawQuery::class.java))
                    funSpec.parameters.first().name
                else
                    null

                addCode(generateQueryCodeBlock(resultType, queryVarsMap, querySql,
                    daoTypeElement, funElement, rawQueryVarName = rawQueryParamName))
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
        val pkEl = entityTypeEl.entityPrimaryKey
            ?: throw IllegalStateException("Could not find primary key for ${entityType.canonicalName}")
        val stmtSql = "DELETE FROM ${entityTypeEl.simpleName} WHERE ${pkEl.simpleName} = ?"
        val firstParam = funSpec.parameters.first()
        var entityVarName = firstParam.name

        addFunction(funSpec.toBuilder()
            .removeAbstractModifier()
            .removeAnnotations()
            .addModifiers(KModifier.OVERRIDE)
            .addCode(CodeBlock.builder()
                .add("var _con = null as %T?\n", Connection::class)
                .add("var _stmt = null as %T?\n", PreparedStatement::class)
                .add("var _numChanges = 0\n")
                .beginControlFlow("try")
                .add("_con = _db.openConnection()\n")
                .add("_stmt = _con.prepareStatement(%S)\n", stmtSql)
                .applyIf(firstParam.type.isListOrArray()) {
                    add("_con.autoCommit = false\n")
                    beginControlFlow("for(_entity in ${firstParam.name})")
                    entityVarName = "_entity"
                }
                .add("_stmt.set${pkEl.asType().asTypeName().preparedStatementSetterGetterTypeName}(1, %L)\n",
                    "$entityVarName.${pkEl.simpleName}")
                .add("_numChanges += _stmt.executeUpdate()\n")
                .applyIf(firstParam.type.isListOrArray()) {
                    endControlFlow()
                    add("_con.commit()\n")
                    add("_con.autoCommit = true\n")
                }
                .beginControlFlow("if(_numChanges > 0)")
                .add("_db.handleTableChanged(listOf(%S))\n", entityTypeEl.simpleName)
                .endControlFlow()
                .nextControlFlow("catch(_e: %T)", SQLException::class)
                .add("_e.printStackTrace()\n")
                .add("throw %T(_e)\n", RuntimeException::class)
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
        val pkEl = entityTypeEl.entityPrimaryKey
            ?: throw IllegalStateException("Could not find primary key for ${entityType.canonicalName}")
        val nonPkFields = entityTypeEl.entityFields.filter { !it.hasAnnotation(PrimaryKey::class.java) }
        val sqlSetPart = nonPkFields.map { "${it.simpleName} = ?" }.joinToString()
        val sqlStmt  = "UPDATE ${entityTypeEl.simpleName} SET $sqlSetPart WHERE ${pkEl.simpleName} = ?"
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
                .add("var _con = null as %T?\n", Connection::class)
                .add("var _stmt = null as %T?\n", PreparedStatement::class)
                .beginControlFlow("try")
                .add("_con = _db.openConnection()!!\n")
                .add("_stmt = _con.prepareStatement(%S)!!\n", sqlStmt)
                .applyIf(firstParam.type.isListOrArray()) {
                    add("_con.autoCommit = false\n")
                        .beginControlFlow("for(_entity in ${firstParam.name})")
                    entityVarName = "_entity"
                }
                .apply {
                    var fieldIndex = 1
                    nonPkFields.forEach {
                        add("_stmt.set${it.asType().asTypeName().preparedStatementSetterGetterTypeName}")
                        add("(%L, %L)\n", fieldIndex++, "$entityVarName.${it.simpleName}")
                    }
                    add("_stmt.set${pkEl.asType().asTypeName().preparedStatementSetterGetterTypeName}")
                    add("(%L, %L)\n", fieldIndex++, "$entityVarName.${pkEl.simpleName}")
                }
                .applyIf(funSpec.hasReturnType) {
                    add("_result += ")
                }
                .apply {
                    add("_stmt.executeUpdate()\n")
                }
                .applyIf(firstParam.type.isListOrArray()) {
                    endControlFlow()
                    add("_con.commit()\n")
                }
                .apply {
                    nextControlFlow("catch(_e: %T)", SQLException::class)
                        .add("_e.printStackTrace()\n")
                        .add("throw %T(_e)\n", RuntimeException::class)
                        .nextControlFlow("finally")
                        .add("_stmt?.close()\n")
                        .add("_con?.close()\n")
                        .endControlFlow()
                        .add("_db.handleTableChanged(listOf(%S))\n", entityTypeEl.simpleName)
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
            .addModifiers(KModifier.OVERRIDE)
            .addCode(CodeBlock.builder()
                .addDaoJdbcInsertCodeBlock(funSpec.parameters.first(),
                funSpec.returnType ?: UNIT,
                    entityTypeSpec,
                    daoTypeBuilder,
                    upsertMode,
                    pgOnConflict = pgOnConflict)
                .build())
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
        pgOnConflict: String? = null
    ): CodeBlock.Builder {
        val paramType = parameterSpec.type
        val entityClassName = paramType.asComponentClassNameIfList()

        val pgOnConflictHash = pgOnConflict?.hashCode()?.let { Math.abs(it) }?.toString() ?: ""
        val entityInserterPropName = "_insertAdapter${entityTypeSpec.name}_${if(upsertMode) "upsert" else ""}$pgOnConflictHash"
        if(!daoTypeBuilder.propertySpecs.any { it.name == entityInserterPropName }) {
            val fieldNames = mutableListOf<String>()
            val parameterHolders = mutableListOf<String>()

            val bindCodeBlock = CodeBlock.builder()
            var fieldIndex = 1
            val pkProp = entityTypeSpec.propertySpecs
                .first { it.annotations.any { it.className == PrimaryKey::class.asClassName()} }

            entityTypeSpec.propertySpecs.forEach { prop ->
                fieldNames.add(prop.name)
                val pkAnnotation = prop.annotations.firstOrNull { it.className == PrimaryKey::class.asClassName() }
                val setterMethodName = prop.type.preparedStatementSetterGetterTypeName
                if(pkAnnotation != null && pkAnnotation.members.findBooleanMemberValue("autoGenerate") ?: false) {
                    parameterHolders.add("\${when(_db.jdbcDbType) { DoorDbType.POSTGRES -> " +
                            "\"COALESCE(?,nextval('${entityTypeSpec.name}_${prop.name}_seq'))\" else -> \"?\"} }")
                    bindCodeBlock.add("when(entity.${prop.name}){ ${defaultVal(prop.type)} " +
                            "-> stmt.setObject(${fieldIndex}, null) " +
                            "else -> stmt.set$setterMethodName(${fieldIndex++}, entity.${prop.name})  }\n")
                }else {
                    parameterHolders.add("?")
                    bindCodeBlock.add("stmt.set$setterMethodName(${fieldIndex++}, entity.${prop.name})\n")
                }
            }

            val statementClause = if(upsertMode) {
                "\${when(_db.jdbcDbType) { DoorDbType.SQLITE -> \"INSERT·OR·REPLACE\" else -> \"INSERT\"} }"
            }else {
                "INSERT"
            }

            val upsertSuffix = if(upsertMode) {
                val nonPkFields = entityTypeSpec.propertySpecs
                    .filter { ! it.annotations.any { it.className == PrimaryKey::class.asClassName() } }
                val nonPkFieldPairs = nonPkFields.map { "${it.name}·=·excluded.${it.name}" }
                val pkField = entityTypeSpec.propertySpecs
                    .firstOrNull { it.annotations.any { it.className == PrimaryKey::class.asClassName()}}
                val pgOnConflictVal = pgOnConflict?.replace(" ", "·") ?: "ON·CONFLICT·(${pkField?.name})·" +
                "DO·UPDATE·SET·${nonPkFieldPairs.joinToString(separator = ",·")}"
                "\${when(_db.jdbcDbType){ DoorDbType.POSTGRES -> \"$pgOnConflictVal\" " +
                        "else -> \"·\" } } "
            } else {
                ""
            }

            val autoGenerateSuffix = " \${when{ _db.jdbcDbType == DoorDbType.POSTGRES && returnsId -> " +
                    "\"·RETURNING·${pkProp.name}·\"  else -> \"\"} } "

            val sql = """
                $statementClause INTO ${entityTypeSpec.name} (${fieldNames.joinToString()})
                VALUES (${parameterHolders.joinToString()})
                $upsertSuffix
                $autoGenerateSuffix
                """.trimIndent()

            val insertAdapterSpec = TypeSpec.anonymousClassBuilder()
                .superclass(EntityInsertionAdapter::class.asClassName().parameterizedBy(entityClassName))
                .addSuperclassConstructorParameter("_db.jdbcDbType")
                .addFunction(FunSpec.builder("makeSql")
                    .addParameter("returnsId", BOOLEAN)
                    .addModifiers(KModifier.OVERRIDE)
                    .addCode("return \"\"\"%L\"\"\"", sql).build())
                .addFunction(FunSpec.builder("bindPreparedStmtToEntity")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("stmt", PreparedStatement::class)
                    .addParameter("entity", entityClassName)
                    .addCode(bindCodeBlock.build()).build())

            daoTypeBuilder.addProperty(PropertySpec.builder(entityInserterPropName,
                EntityInsertionAdapter::class.asClassName().parameterizedBy(entityClassName))
                .initializer("%L", insertAdapterSpec.build())
                .build())
        }



        if(returnType != UNIT) {
            add("val _retVal = ")
        }


        val insertMethodName = makeInsertAdapterMethodName(paramType, returnType, processingEnv)
        add("$entityInserterPropName.$insertMethodName(${parameterSpec.name}, _db.openConnection())")

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

        add("_db.handleTableChanged(listOf(%S))\n", entityTypeSpec.name)

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


    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val dbs = roundEnv.getElementsAnnotatedWith(Database::class.java)

        for(dbTypeEl in dbs) {
            val dbFileSpec = generateDbImplClass(dbTypeEl as TypeElement)
            writeFileSpecToOutputDirs(dbFileSpec, OPTION_JVM_DIRS)
            FileSpec.builder(dbTypeEl.packageName, dbTypeEl.simpleName.toString() + DoorDatabaseMetadata.SUFFIX_DOOR_METADATA)
                .addDatabaseMetadataType(dbTypeEl, processingEnv)
                .build()
                .writeToDirsFromArg(listOf(OPTION_JVM_DIRS, OPTION_ANDROID_OUTPUT))
        }


        val daos = roundEnv.getElementsAnnotatedWith(Dao::class.java)

        for(daoElement in daos) {
            val daoTypeEl = daoElement as TypeElement
            FileSpec.builder(daoElement.packageName,
                daoElement.simpleName.toString() + SUFFIX_JDBC_KT2)
                .addDaoJdbcImplType(daoTypeEl)
                .build()
                .writeToDirsFromArg(listOf(OPTION_JVM_DIRS))
        }

        return true
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
                    daoImplClassName).delegate("lazy·{·%T(this)·}", daoImplClassName).build())

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

            codeBlock.endControlFlow()
        }


        codeBlock.endControlFlow() //end when
                .nextControlFlow("catch(e: %T)", Exception::class)
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

        dropFunSpec.nextControlFlow("finally")
                .addCode("_stmt?.close()\n")
                .addCode("_con?.close()\n")
                .endControlFlow()

        return dropFunSpec.build()
    }

    fun makeLogPrefix(enclosing: TypeElement, method: ExecutableElement) = "DoorDb: ${enclosing.qualifiedName}. ${method.simpleName} "

    companion object {

        const val SUFFIX_JDBC_KT = "JdbcKt"

        //As it should be including the underscore - the above will be deprecated
        const val SUFFIX_JDBC_KT2 = "_JdbcKt"

        const val ARG_MIGRATION_TEMPLATE_SQLITE_UPDATE_TRIGGER = "doordb_template_fixupdatetrigger_sqlite"

    }
}