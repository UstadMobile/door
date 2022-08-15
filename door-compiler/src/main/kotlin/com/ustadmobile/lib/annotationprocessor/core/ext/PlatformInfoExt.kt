package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.processing.JsPlatformInfo
import com.google.devtools.ksp.processing.JvmPlatformInfo
import com.google.devtools.ksp.processing.PlatformInfo
import com.ustadmobile.lib.annotationprocessor.core.DoorTarget

fun PlatformInfo.doorTarget(): DoorTarget {
    return when {
        this is JvmPlatformInfo -> DoorTarget.JVM
        this is JsPlatformInfo -> DoorTarget.JS
        platformName.indexOf("Android", ignoreCase = true) != -1 -> DoorTarget.ANDROID
        else -> throw IllegalStateException("Door: Unsupported platform: $platformName")
    }
}