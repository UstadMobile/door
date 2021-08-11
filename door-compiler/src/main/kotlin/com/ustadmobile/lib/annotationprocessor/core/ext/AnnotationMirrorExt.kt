package com.ustadmobile.lib.annotationprocessor.core.ext

import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.TypeElement
import kotlin.reflect.KClass

fun List<AnnotationMirror>.findByClass(processingEnv: ProcessingEnvironment, klass: KClass<*>): AnnotationMirror? {
    for(annotationMirror in this) {
        val annotationTypeEl = processingEnv.typeUtils
            .asElement(annotationMirror.getAnnotationType()) as TypeElement

        if(annotationTypeEl.qualifiedName.toString() == klass.qualifiedName) {
            return annotationMirror
        }
    }

    return null
}