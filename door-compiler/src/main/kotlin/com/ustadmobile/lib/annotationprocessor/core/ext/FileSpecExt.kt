package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.processing.CodeGenerator
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import com.ustadmobile.lib.annotationprocessor.core.DoorTarget
import java.io.File

fun FileSpec.writeToPlatformDir(
    target: DoorTarget,
    codeGenerator: CodeGenerator,
    options: Map<String, String>,
) {
    val targetDir = options[target.outputArgName]
    if(targetDir != null) {
        writeTo(File(targetDir))
    }else {
        writeTo(codeGenerator, false)
    }
}
