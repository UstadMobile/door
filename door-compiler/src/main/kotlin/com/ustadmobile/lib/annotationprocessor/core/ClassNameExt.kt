package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Embedded
import com.squareup.kotlinpoet.*
import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.door.annotation.SyncableEntity
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement

/**
 * Replaces AbstractDbProcessor.findEntitiesWithAnnotation
 *
 * This searches the given entity itself and all EmbeddedEntities
 */
fun ClassName.findAllEntitiesWithAnnotation(annotationClass: Class<out Annotation>,
                               processingEnv: ProcessingEnvironment,
                               embedPath: List<String> = listOf()): Map<List<String>, ClassName> {
    if(this in QUERY_SINGULAR_TYPES)
        return mapOf()


    val entityTypeEl = processingEnv.elementUtils.getTypeElement(canonicalName) ?: return mapOf()

    val syncableEntityList = mutableMapOf<List<String>, ClassName>()

    entityTypeEl.ancestorsAsList(processingEnv).forEach {
        if(it.getAnnotation(annotationClass) != null)
            syncableEntityList.put(embedPath, it.asClassName())

        it.enclosedElements.filter { it.getAnnotation(Embedded::class.java) != null}.forEach {
            val subEmbedPath = mutableListOf(*embedPath.toTypedArray()) + "${it.simpleName}"
            val subClassName = it.asType().asTypeName() as ClassName
            syncableEntityList.putAll(subClassName.findAllEntitiesWithAnnotation(annotationClass,
                    processingEnv, subEmbedPath))
        }
    }

    return syncableEntityList.toMap()
}

/**
 * Replaces AbstractDbProcessor.findSyncableEntities
 *
 * Get a list of all the syncable entities associated with a given POJO. This will look at parent
 * classes and embedded fields
 *
 * @param entityType the POJO to inspect to find syncable entities. This will inspect the class
 * itself, the parent classes, and any fields annotated with Embedded
 * @param processingEnv the annotation processor environment
 * @param embedPath the current embed path. This function is designed to work recursively.
 *
 * @return A map in the form of a list of the embedded variables to the syncable entity
 * e.g.
 * given
 *
 * <pre>
 * class SyncableEntityWithOtherSyncableEntity(@Embedded var embedded: OtherSyncableEntity?): SyncableEntity()
 * </pre>
 * This will result in:
 * <pre>
 * {
 * [] -> SyncableEntity,
 * ['embedded'] -> OtherSyncableEntity
 * }
 * </pre>
 */
fun ClassName.findAllSyncableEntities(processingEnv: ProcessingEnvironment,
                         embedPath: List<String> = listOf()) =
        findAllEntitiesWithAnnotation(SyncableEntity::class.java, processingEnv, embedPath)

fun ClassName.entitySyncableTypes(processingEnv: ProcessingEnvironment): List<ClassName> {
    return findAllSyncableEntities(processingEnv).values.toList()
}

/**
 * Shorthand to check if this entity (or any of it's parents or embedded entities) contain
 * syncable entities
 */
fun ClassName.entityHasSyncableEntityTypes(processingEnv: ProcessingEnvironment) : Boolean
    = findAllSyncableEntities(processingEnv).isNotEmpty()


/**
 * Convenience shorthand for creating a new classname with the given suffix and the same package
 * as the original
 */
fun ClassName.withSuffix(suffix: String) = ClassName(this.packageName, "$simpleName$suffix")

/**
 * Create a TypeSpec that represents an entity from a ClassName. There can be two cases:
 * 1) The ClassName refers to a generated _trk entity. No TypeElement will be available for this as
 *   it is itself generated and not part of the compilation source being processed. This function
 *   will look up the original entity itself and generate a TypeSpec for the tracker from that
 *
 * 2) The ClassName refers to an actual class that is part of the compilation source annotated by
 * Entity - in which case we will just look it up using processingEnv
 */
fun ClassName.asEntityTypeSpec(processingEnv: ProcessingEnvironment): TypeSpec? {
    if(simpleName.endsWith("_trk")) {
        val originalEntityTypeEl = processingEnv.elementUtils.getTypeElement(
                this.canonicalName.removeSuffix("_trk"))
        return generateTrackerEntity(originalEntityTypeEl, processingEnv)
    }else {
        val entityTypeEl = processingEnv.elementUtils.getTypeElement(canonicalName) as? TypeElement
        return entityTypeEl?.asEntityTypeSpec()
    }
}

fun ClassName.asTypeElement(processingEnv: ProcessingEnvironment): TypeElement? {
    return processingEnv.elementUtils.getTypeElement(canonicalName) as? TypeElement
}

/**
 * Where the ClassName represents something that we can find as a TypeElement,
 * check if the actual declaration of the class has any of the given annotations.
 */
fun <A : Annotation> ClassName.hasAnyAnnotation(
    processingEnv: ProcessingEnvironment,
    vararg annotationsClasses: Class<out A>
): Boolean {
    return asTypeElement(processingEnv)?.hasAnyAnnotation(*annotationsClasses) ?: false
}

/**
 * Returns true if the given ClassName represents the ReplicateEntity itself, NOT if it is a child class of a
 * ReplicateEntity etc
 */
fun ClassName.isReplicateEntity(
    processingEnv: ProcessingEnvironment
) = hasAnyAnnotation(processingEnv, ReplicateEntity::class.java)
