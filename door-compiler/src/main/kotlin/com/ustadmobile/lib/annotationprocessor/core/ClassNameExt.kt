package com.ustadmobile.lib.annotationprocessor.core

import com.squareup.kotlinpoet.*
import com.ustadmobile.door.annotation.ReplicateEntity
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement


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
    val entityTypeEl = processingEnv.elementUtils.getTypeElement(canonicalName)
    return entityTypeEl?.asEntityTypeSpec()
}

fun ClassName.asTypeElement(processingEnv: ProcessingEnvironment): TypeElement? {
    return processingEnv.elementUtils.getTypeElement(canonicalName)
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
