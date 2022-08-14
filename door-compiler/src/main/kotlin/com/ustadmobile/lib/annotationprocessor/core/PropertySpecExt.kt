package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.PrimaryKey
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asTypeName

fun PropertySpec.Builder.delegateGetter(delegate: String) : PropertySpec.Builder{
    getter(FunSpec.getterBuilder()
            .addCode("return $delegate\n")
            .build())
    return this
}

fun PropertySpec.Builder.delegateSetter(delegate: String) : PropertySpec.Builder {
    setter(FunSpec.setterBuilder()
            .addParameter("value", Any::class)
            .addCode("$delegate = value\n")
            .build())
    return this
}

fun PropertySpec.Builder.delegateGetterAndSetter(delegate: String) : PropertySpec.Builder {
    delegateGetter(delegate)
    delegateSetter(delegate)
    return this
}

