package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Dao
import com.squareup.kotlinpoet.*
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.*
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType
import androidx.room.*
import com.squareup.kotlinpoet.metadata.toKmClass
import com.ustadmobile.door.annotation.*
import com.ustadmobile.lib.annotationprocessor.core.AbstractDbProcessor.Companion.SUFFIX_DEFAULT_RECEIVEVIEW
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_HELPER
import com.ustadmobile.lib.annotationprocessor.core.ext.findByClass
import com.ustadmobile.lib.annotationprocessor.core.ext.getClassArrayValue
import kotlinx.metadata.KmProperty
import javax.lang.model.type.TypeMirror

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


internal fun TypeElement.asEntityTypeSpec() = this.asEntityTypeSpecBuilder().build()

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

val TypeElement.entityAttachmentAnnotatedFields: List<Element>
    get() = enclosedElementsWithAnnotation(AttachmentUri::class.java) +
            enclosedElementsWithAnnotation(AttachmentSize::class.java) +
            enclosedElementsWithAnnotation(AttachmentMd5::class.java)


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

fun  <A: Annotation> TypeElement.kmPropertiesWithAnnotation(annotationClass: Class<A>): List<KmProperty> {
    val kmClass = getAnnotation(Metadata::class.java).toKmClass()
    val elementsWithAnnotationNames = enclosedElementsWithAnnotation(annotationClass).map { it.simpleName.toString() }
    return kmClass.properties.filter { it.name in elementsWithAnnotationNames }
}

