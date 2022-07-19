package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile

fun TypeSpec.Builder.addOriginatingKsFileOrThrow(ksFile: KSFile?) : TypeSpec.Builder{
    return addOriginatingKSFile(ksFile ?: throw IllegalArgumentException("addOriginatingKsFileOrThrow: is null"))
}