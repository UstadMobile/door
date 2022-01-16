package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Dao
import com.squareup.kotlinpoet.*
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.*
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType
import androidx.room.*
import com.squareup.kotlinpoet.metadata.ImmutableKmProperty
import com.squareup.kotlinpoet.metadata.toImmutableKmClass
import com.ustadmobile.door.annotation.*
import com.ustadmobile.lib.annotationprocessor.core.AbstractDbProcessor.Companion.SUFFIX_DEFAULT_RECEIVEVIEW
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_HELPER
import com.ustadmobile.lib.annotationprocessor.core.ext.findByClass
import com.ustadmobile.lib.annotationprocessor.core.ext.getClassArrayValue
import javax.lang.model.type.TypeMirror

val ALL_QUERY_ANNOTATIONS = listOf(Query::class.java, Update::class.java, Delete::class.java,
        Insert::class.java)

internal fun TypeElement.asEntityTypeSpecBuilder(): TypeSpec.Builder {
    val typeSpecBuilder = TypeSpec.classBuilder(this.simpleName.toString())
    this.enclosedElements
            .filter { it.kind == ElementKind.FIELD
                    && it.simpleName.toString() != "Companion"
                    && Modifier.STATIC !in it.modifiers }
            .forEach {
        val propSpec = PropertySpec.builder(it.simpleName.toString(),
                it.asType().asTypeName().javaToKotlinType())
        propSpec.addAnnotations( it.annotationMirrors.map { AnnotationSpec.get(it) })
        typeSpecBuilder.addProperty(propSpec.build())
    }

    return typeSpecBuilder
}

/**
 * Provides a list of the indexes that are on a given table. Using the Index value itself can lead
 * to classnotfound errors. Adding this to a TypeSpec leads to having to parse strings with arrays,
 * quotes, etc.
 */
internal fun TypeElement.indicesAsIndexMirrorList() =
        getAnnotation(Entity::class.java).indices.map { IndexMirror(it) }


internal fun TypeElement.asEntityTypeSpec() = this.asEntityTypeSpecBuilder().build()

internal fun TypeElement.hasDataSourceFactory(paramTypeFilter: (List<TypeName>) -> Boolean = {true})
        = enclosedElements.any { it.kind == ElementKind.METHOD
        && (it as ExecutableElement).returnType.asTypeName().isDataSourceFactory(paramTypeFilter) }

/**
 * Get a list of all the methods of the given TypeElement including those that are declared in
 * parent classes and interfaces that can be overriden. This function can exclude methods that are
 * already implemented in parent classes (including resolving generic types as required)
 */
fun TypeElement.allOverridableMethods(processingEnv: ProcessingEnvironment,
                                      enclosing: DeclaredType = this.asType() as DeclaredType,
                                      includeImplementedMethods: Boolean = false) :List<ExecutableElement> {
    return ancestorsAsList(processingEnv).flatMap {
        it.enclosedElements.filter {
            it.kind ==  ElementKind.METHOD
                    && (includeImplementedMethods || Modifier.ABSTRACT in it.modifiers) //abstract methods in this class
                    && (Modifier.FINAL !in it.modifiers)
        } + it.interfaces.flatMap {
            processingEnv.typeUtils.asElement(it).enclosedElements.filter { it.kind == ElementKind.METHOD } //methods from the interface
        }
    }.filter {
        includeImplementedMethods || !isMethodImplemented(it as ExecutableElement, this, processingEnv)
    }.distinctBy {
        val signatureParamTypes = (processingEnv.typeUtils.asMemberOf(enclosing, it) as ExecutableType)
                .parameterTypes.filter { ! isContinuationParam(it.asTypeName()) }
        MethodToImplement(it.simpleName.toString(), signatureParamTypes.map { it.asTypeName() })
    }.map {
        it as ExecutableElement
    }
}

/**
 * Shorthand extension for a TypeElement that represents a class with the @Database annotation
 * that will give a list of all the DAO getter functions
 */
fun TypeElement.allDbClassDaoGetters(processingEnv: ProcessingEnvironment) =
        allOverridableMethods(processingEnv)
            .filter { it.returnType.asTypeElement(processingEnv)?.hasAnnotation(Dao::class.java) == true}

/**
 * Where this TypeElement represents a Database, this is a shorthand that gives all the DAO classes
 * that require a repository
 */
fun TypeElement.allDbClassDaoGettersWithRepo(processingEnv: ProcessingEnvironment) =
        allDbClassDaoGetters(processingEnv).filter {
            it.returnType.asTypeElement(processingEnv)?.isDaoWithRepository == true
        }

/**
 * Gets a list of all ancestor parent classes and interfaces.
 */
fun TypeElement.ancestorsAsList(processingEnv: ProcessingEnvironment): List<TypeElement> {
    val entityAncestors = mutableListOf<TypeElement>()

    var nextEntity = this as TypeElement?

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

/**
 * Get a list of all the methods on this TypeElement that have any of the given annotations
 */
fun <A: Annotation> TypeElement.allMethodsWithAnnotation(annotationList: List<Class<out A>>): List<ExecutableElement> {
    return enclosedElements.filter { subEl ->
        subEl.kind == ElementKind.METHOD && annotationList.any { subEl.hasAnnotation(it) }
    }.map {
        it as ExecutableElement
    }
}

/**
 * Get a list of all the methods on this DAO that have a query that could modify the database
 */
fun TypeElement.allDaoClassModifyingQueryMethods(
    checkQueryAnnotation: Boolean = true
) : List<ExecutableElement> {
    val annotations = listOf(Update::class.java, Delete::class.java, Insert::class.java) +
            if(checkQueryAnnotation) listOf(Query::class.java) else listOf()

    return allMethodsWithAnnotation(annotations).filter {
        if(it.hasAnnotation(Query::class.java)) {
            it.getAnnotation(Query::class.java).value.isSQLAModifyingQuery()
        }else {
            true
        }
    }
}

/**
 * Where the TypeElement represents a database class, get a list of TypeElements representing
 * all the entities as per the @Database annotation.
 */
@Suppress("UNCHECKED_CAST")
fun TypeElement.allDbEntities(processingEnv: ProcessingEnvironment): List<TypeElement> {
    val dbAnnotationMirror = annotationMirrors.findByClass(processingEnv, Database::class)
        ?: throw IllegalArgumentException("allDbEntities: ${this.qualifiedName} has no database annotation!")
    return dbAnnotationMirror.getClassArrayValue("entities", processingEnv)
}

/**
 * Where this TypeElement represents a database class, get a list of all entities that have
 * an attachment.
 */
fun TypeElement.allEntitiesWithAttachments(processingEnv: ProcessingEnvironment) =
        allDbEntities(processingEnv).filter {
            it.enclosedElementsWithAnnotation(AttachmentUri::class.java).isNotEmpty()
        }


fun TypeElement.asClassNameWithSuffix(suffix: String) =
        ClassName(packageName, "$simpleName$suffix")


/**
 * Indicates whether or not this database should have a read only wrapper generated. The ReadOnlyWrapper should be
 * be generated for databses that have repositories and replicated entities.
 */
fun TypeElement.dbHasReplicateWrapper(processingEnv: ProcessingEnvironment) : Boolean {
    val hasRepos = dbEnclosedDaos(processingEnv).any { it.hasAnnotation(Repository::class.java) }
    val hasReplicatedEntities = allDbEntities(processingEnv).any { it.hasAnnotation(ReplicateEntity::class.java) }
    return hasRepos && hasReplicatedEntities
}


/**
 * If the given TypeElement represents a Dao, this will return a map of the ClassNames for DAOs
 * for the primary and local KTOR Helper Daos that are required.
 */
val TypeElement.daoKtorHelperDaoClassNames: Map<String, ClassName>
    get() = mapOf("Master" to this.asClassNameWithSuffix("${SUFFIX_KTOR_HELPER}Master"),
        "Local" to this.asClassNameWithSuffix("${SUFFIX_KTOR_HELPER}Local"))


/**
 * Where this TypeElement represents a DAO, this is a shorthand to provide a list of all methods on
 * the DAO which are annotated with any kind of Query (inc Query, Delete, Update, Insert)
 */
fun TypeElement.allDaoQueryMethods() = allMethodsWithAnnotation(ALL_QUERY_ANNOTATIONS)

/**
 * Convert this TypeElement into a Kotlin Poet FunSpec that can be used to generate implementations.
 * TypeSpecs are often used as the basis for generation logic because we can generate it (eg. for
 * SyncDaos etc). This extension function converts a TypeElement from the annotation processor
 * environment into an equivalent TypeSpec.
 *
 */
fun TypeElement.asTypeSpecStub(processingEnv: ProcessingEnvironment): TypeSpec {
    val declaredType = this.asType() as DeclaredType
    val thisTypeEl = this
    return TypeSpec.classBuilder(asClassName())
            .applyIf(Modifier.ABSTRACT in modifiers) {
                addModifiers(KModifier.ABSTRACT)
            }
            .apply {
                allOverridableMethods(processingEnv).forEach {executableEl ->
                    val resolvedType = executableEl.asMemberOf(thisTypeEl, processingEnv)
                    val returnTypeName = resolvedType.suspendedSafeReturnType

                    addFunction(executableEl.asFunSpecConvertedToKotlinTypes(declaredType,
                        processingEnv, forceNullableReturn = returnTypeName.isNullableAsSelectReturnResult,
                        forceNullableParameterTypeArgs = returnTypeName.isNullableParameterTypeAsSelectReturnResult)
                            .build())
                }
            }
            .build()
}

/**
 * Check to see if this represents a TypeElement for a Dao which is annotated with Repository.
 *
 * This means that repository classes for this should be generated.
 */
val TypeElement.isDaoWithRepository: Boolean
    get() = this.getAnnotation(Dao::class.java) != null
            && this.getAnnotation(Repository::class.java) != null

/**
 * Determines if this DAO requires a KTOR Helper (it has return results with syncable entities and
 * is annotated with @Repository)
 *
 * TODO: Check to make sure that at least one of the select queries returns a syncable entity type
 */
val TypeElement.isDaoThatRequiresKtorHelper: Boolean
    get() = isDaoWithRepository


fun TypeElement.dbEnclosedDaos(processingEnv: ProcessingEnvironment) : List<TypeElement> {
    return enclosedElements
            .filter { it.kind == ElementKind.METHOD && Modifier.ABSTRACT in it.modifiers}
            .mapNotNull { (it as ExecutableElement).returnType.asTypeElement(processingEnv) }
            .filter { it.hasAnnotation(Dao::class.java) }
}

/**
 * Shorthand to check if this is an entity that has attachments
 */
val TypeElement.entityHasAttachments: Boolean
    get() = hasAnnotation(Entity::class.java) &&
            enclosedElementsWithAnnotation(AttachmentUri::class.java, ElementKind.FIELD).isNotEmpty()


val TypeElement.entityPrimaryKey: Element?
    get() = enclosedElements.firstOrNull {
        it.kind == ElementKind.FIELD && it.hasAnnotation(PrimaryKey::class.java)
    }

/**
 * The preferred shorthand to get a list of the primary keys for a given table. The primary key could be
 */
val TypeElement.entityPrimaryKeys: List<VariableElement>
    get() {
        val entityPkVarNames = getAnnotation(Entity::class.java).primaryKeys
        val annotatedPkVar = enclosedElements.firstOrNull {
            it.kind == ElementKind.FIELD && it.hasAnnotation(PrimaryKey::class.java)
        } as? VariableElement

        if(annotatedPkVar != null) {
            return listOf(annotatedPkVar)
        }else {
            return entityPkVarNames.mapNotNull { varName ->
                enclosedElements.firstOrNull {
                    it.kind == ElementKind.FIELD && it.simpleName.toString() == varName
                } as? VariableElement
            }
        }
    }


val TypeElement.entityFields: List<Element>
    get() = enclosedElements.filter {
        it.kind == ElementKind.FIELD && it.simpleName.toString() != "Companion"
                && !it.modifiers.contains(Modifier.STATIC)
    }

/**
 * This is a placeholder that will, in future, allow for support of instances where the table name is different to the
 * entity
 */
val TypeElement.entityTableName: String
    get() = simpleName.toString()


/**
 * If the given element is an entity with the ReplicateEntity annotation, this will provide the TypeElement that
 * represents the tracker entity
 */
fun TypeElement.getReplicationTracker(processingEnv: ProcessingEnvironment): TypeElement {
    val repAnnotation = annotationMirrors.findByClass(processingEnv, ReplicateEntity::class)
        ?: throw IllegalArgumentException("Class ${this.qualifiedName} has no replicate entity annotation")

    val repTrkr = repAnnotation.elementValues.entries.first { it.key.simpleName.toString() == "tracker" }
    val trkrTypeMirror = repTrkr.value.value as TypeMirror
    return processingEnv.typeUtils.asElement(trkrTypeMirror) as TypeElement
}

/**
 * Where this is a TypeElement representing a ReplicateEntity, provide the view name for the receive view
 */
val TypeElement.replicationEntityReceiveViewName: String
    get() = getAnnotation(ReplicateReceiveView::class.java)?.name ?: (simpleName.toString() + SUFFIX_DEFAULT_RECEIVEVIEW)

val TypeElement.replicationTrackerForeignKey: Element
    get() = enclosedElementsWithAnnotation(ReplicationEntityForeignKey::class.java, ElementKind.FIELD).firstOrNull()
        ?: throw IllegalArgumentException("${this.qualifiedName} has no replicationentityforeignkey")

fun <A: Annotation> TypeElement.firstFieldWithAnnotation(annotationClass: Class<A>): Element {
    return enclosedElementsWithAnnotation(annotationClass).first()
}

fun <A: Annotation> TypeElement.firstFieldWithAnnotationNameOrNull(annotationClass: Class<A>): CodeBlock {
    val annotatedElement = enclosedElementsWithAnnotation(annotationClass).firstOrNull()
    return if(annotatedElement != null) {
        CodeBlock.of("%S", annotatedElement.simpleName)
    }else {
        CodeBlock.of("null")
    }
}

fun  <A: Annotation> TypeElement.kmPropertiesWithAnnotation(annotationClass: Class<A>): List<ImmutableKmProperty> {
    val kmClass = getAnnotation(Metadata::class.java).toImmutableKmClass()
    val elementsWithAnnotationNames = enclosedElementsWithAnnotation(annotationClass).map { it.simpleName.toString() }
    return kmClass.properties.filter { it.name in elementsWithAnnotationNames }
}

fun TypeElement.findDaoGetter(daoTypeElement: TypeElement, processingEnv: ProcessingEnvironment): CodeBlock {
    val executableFunEl = enclosedElements
        .first { it.kind == ElementKind.METHOD && (it as ExecutableElement).returnType.asTypeElement(processingEnv) == daoTypeElement }
    return (executableFunEl as ExecutableElement).makeAccessorCodeBlock()
}

val TypeElement.isReplicateEntityWithAutoIncPrimaryKey: Boolean
    get() {
        if(!hasAnnotation(ReplicateEntity::class.java))
            return false

        val pkEntity = enclosedElementsWithAnnotation(PrimaryKey::class.java, ElementKind.FIELD).firstOrNull() ?: return false

        return pkEntity.getAnnotation(PrimaryKey::class.java).autoGenerate
    }
