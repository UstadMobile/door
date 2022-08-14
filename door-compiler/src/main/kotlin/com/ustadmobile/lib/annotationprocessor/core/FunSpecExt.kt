package com.ustadmobile.lib.annotationprocessor.core

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.UNIT


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
fun FunSpec.Builder.removeAbstractModifier(): FunSpec.Builder {
    if(KModifier.ABSTRACT in modifiers)
        modifiers.remove(KModifier.ABSTRACT)

    return this
}

fun FunSpec.Builder.removeAnnotations(): FunSpec.Builder {
    annotations.clear()
    return this
}

