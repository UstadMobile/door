package com.ustadmobile.lib.annotationprocessor.core

import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement
import kotlin.reflect.KClass

/**
 * Simple convenience wrapper that will provide a TypeElement for the given KClass object
 */
fun KClass<*>.asTypeElement(processingEnvironment: ProcessingEnvironment) : TypeElement{
    return processingEnvironment.elementUtils.getTypeElement(qualifiedName)
}
