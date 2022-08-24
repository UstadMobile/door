package com.ustadmobile.lib.annotationprocessor.core

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toAnnotationSpec


//Shorthand to check if this function is suspended
val FunSpec.isSuspended: Boolean
    get() = KModifier.SUSPEND in modifiers


/**
 * Shorthand to check if this function has an actual return type
 */
val FunSpec.hasReturnType: Boolean
    get() = returnType != null && returnType != UNIT


/**
 * Shorthand to make the function non-abstract
 */
fun FunSpec.Builder.removeAbstractModifier() = removeModifier(KModifier.ABSTRACT)

fun FunSpec.Builder.removeModifier(modifier: KModifier) : FunSpec.Builder {
    if(modifier in modifiers)
        modifiers.remove(modifier)

    return this
}

fun FunSpec.Builder.removeAnnotations(): FunSpec.Builder {
    annotations.clear()
    return this
}

fun FunSpec.Builder.copyAnnotations(
    ksAnnotated: KSAnnotated,
    filter: (KSAnnotation) -> Boolean,
) : FunSpec.Builder {
    ksAnnotated.annotations.filter(filter).forEach {
        addAnnotation(it.toAnnotationSpec())
    }

    return this
}

fun FunSpec.Builder.addOriginatingKSClass(
    ksClassDeclaration: KSClassDeclaration
): FunSpec.Builder {
    ksClassDeclaration.containingFile?.also {
        addOriginatingKSFile(it)
    }

    return this
}
