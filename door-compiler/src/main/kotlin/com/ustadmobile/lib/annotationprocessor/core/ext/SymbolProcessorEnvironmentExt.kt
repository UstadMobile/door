package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.processing.JsPlatformInfo
import com.google.devtools.ksp.processing.JvmPlatformInfo
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.ustadmobile.lib.annotationprocessor.core.DoorTarget

fun SymbolProcessorEnvironment.doorTarget(resolver: Resolver): DoorTarget {
    val activityClassDecl = resolver.getClassDeclarationByName(
        resolver.getKSNameFromString("android.app.Activity"))
    return when {
        activityClassDecl != null -> DoorTarget.ANDROID
        (platforms.firstOrNull() is JvmPlatformInfo) -> DoorTarget.JVM
        (platforms.firstOrNull() is JsPlatformInfo) -> DoorTarget.JS
        else -> throw IllegalStateException("Door: Unsupported platform: ${platforms.firstOrNull()?.platformName}")
    }
}
