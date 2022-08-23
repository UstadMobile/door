package com.ustadmobile.lib.annotationprocessor.core.ext

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec

fun PropertySpec.Builder.removeModifier(modifier: KModifier) : PropertySpec.Builder {
    if(modifier in modifiers)
        modifiers.remove(modifier)

    return this
}