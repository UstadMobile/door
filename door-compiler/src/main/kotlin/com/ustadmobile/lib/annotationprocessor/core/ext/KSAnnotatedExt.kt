package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import kotlin.reflect.KClass

/**
 * As per experimental getAnnotationsByType, but returns the KSAnnotation instance
 */
fun <T: Annotation> KSAnnotated.getKSAnnotationsByType(annotationKClass: KClass<T>): Sequence<KSAnnotation> {
    return annotations.filter { it.isAnnotationClass(annotationKClass) }
}

fun <T: Annotation> KSAnnotated.getKSAnnotationByType(annotationKClass: KClass<T>): KSAnnotation? {
    return getKSAnnotationsByType(annotationKClass).firstOrNull()
}

/**
 * Wrapper to avoid OptIn. This implementation is trivial and could be changed.
 */
@OptIn(KspExperimental::class)
fun <T: Annotation> KSAnnotated.hasAnnotation(annotationKClazz: KClass<T>): Boolean {
    return isAnnotationPresent(annotationKClazz)
}

fun <T: Annotation> KSAnnotated.hasAnyAnnotation(vararg annotationKClass: KClass<out T>): Boolean {
    return annotationKClass.any { this.hasAnnotation(it) }
}


@OptIn(KspExperimental::class)
fun <T: Annotation> KSAnnotated.getAnnotation(annotationKClazz: KClass<T>): T? {
    return getAnnotationsByType(annotationKClazz).firstOrNull()
}

@OptIn(KspExperimental::class)
fun <T: Annotation> KSAnnotated.getAnnotations(annotationKClazz: KClass<T>): List<T> {
    return getAnnotationsByType(annotationKClazz).toList()
}

@OptIn(KspExperimental::class)
fun <T: Annotation> KSAnnotated.getAnnotationOrNull(annotationKClazz: KClass<T>): T? {
    return getAnnotationsByType(annotationKClazz).firstOrNull()
}

