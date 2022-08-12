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

