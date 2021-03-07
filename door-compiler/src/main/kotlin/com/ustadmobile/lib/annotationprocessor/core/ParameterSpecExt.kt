package com.ustadmobile.lib.annotationprocessor.core

import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.asClassName

fun <A: Annotation> ParameterSpec.hasAnnotation(annotation: Class<A>): Boolean {
    return annotations.any { it.className == annotation.asClassName() }
}