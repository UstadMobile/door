package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toAnnotationSpec

fun TypeSpec.Builder.addOriginatingKsFileOrThrow(ksFile: KSFile?) : TypeSpec.Builder{
    return addOriginatingKSFile(ksFile ?: throw IllegalArgumentException("addOriginatingKsFileOrThrow: is null"))
}

fun TypeSpec.Builder.copyAnnotations(
    ksAnnotated: KSAnnotated,
    filter: (KSAnnotation) -> Boolean = { true }
): TypeSpec.Builder {
    ksAnnotated.annotations.filter(filter).forEach {
        addAnnotation(it.toAnnotationSpec())
    }
    return this
}