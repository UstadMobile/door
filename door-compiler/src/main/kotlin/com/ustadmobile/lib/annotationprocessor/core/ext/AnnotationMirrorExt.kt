package com.ustadmobile.lib.annotationprocessor.core.ext

import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import kotlin.reflect.KClass

fun List<AnnotationMirror>.findByClass(processingEnv: ProcessingEnvironment, klass: KClass<out Annotation>): AnnotationMirror? {
    for(annotationMirror in this) {
        val annotationTypeEl = processingEnv.typeUtils
            .asElement(annotationMirror.getAnnotationType()) as TypeElement

        if(annotationTypeEl.qualifiedName.toString() == klass.qualifiedName) {
            return annotationMirror
        }
    }

    return null
}

fun List<AnnotationMirror>.filterByClass(processingEnv: ProcessingEnvironment, klass: KClass<out Annotation>): List<AnnotationMirror> {
    return filter {
        val typeEl = processingEnv.typeUtils.asElement(it.annotationType) as? TypeElement
        typeEl?.qualifiedName.toString() == klass.qualifiedName
    }
}

/**
 * Where an annotation class contains Class values, the AnnotationValue.value object will
 * be a list of AnnotationValues, and their .value objects will be a TypeMirror
 */
@Suppress("UNCHECKED_CAST")
private fun AnnotationValue.mapAnnotationValuesToTypeElements(
    processingEnv: ProcessingEnvironment
) : List<TypeElement> {
    return (value as? List<AnnotationValue>)?.mapNotNull { processingEnv.typeUtils.asElement(it.value as TypeMirror) as? TypeElement } ?: listOf()
}

/**
 * Given an Annotation that has an array of classes e.g.
 *
 * @Annotation(valueKey = [Clazz::class ...])
 *
 * This function will return a List of TypeElements representing the classes in the array
 */
@Suppress("UNCHECKED_CAST")
fun AnnotationMirror.getClassArrayValue(valueKey: String, processingEnv: ProcessingEnvironment): List<TypeElement> {
    val annotationValueEntry = elementValues.entries.firstOrNull { it.key.simpleName.toString() == valueKey }?.value
    return annotationValueEntry?.mapAnnotationValuesToTypeElements(processingEnv) ?: listOf()
}

fun AnnotationMirror.getClassValue(valueKey: String, processingEnv: ProcessingEnvironment): TypeElement? {
    val annotationValueEntry = elementValues.entries.firstOrNull { it.key.simpleName.toString() == valueKey }?.value
     return annotationValueEntry?.mapAnnotationValuesToTypeElements(processingEnv)?.firstOrNull()
}

