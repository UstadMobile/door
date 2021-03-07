package com.ustadmobile.lib.annotationprocessor.core

import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * Shorthand to convert the given TypeMirror to a TypeElement using processingEnv if it represents
 * a TypeElement (e.g. a class)
 */
fun TypeMirror.asTypeElement(processingEnv: ProcessingEnvironment): TypeElement? =
        processingEnv.typeUtils.asElement(this) as? TypeElement

/**
 * Unwrap the component type if this type mirror represents an array or a list. Otherwise return
 * the type mirror itself
 */
fun TypeMirror.unwrapListOrArrayComponentType(): TypeMirror =
        if(this.kind == TypeKind.ARRAY) {
            (this as ArrayType).componentType
        }else if(this.kind == TypeKind.DECLARED && this is DeclaredType
                && (this.asElement() as? TypeElement)?.asClassName() == List::class.asClassName()) {
            this.typeArguments[0]
        }else {
            this
        }
