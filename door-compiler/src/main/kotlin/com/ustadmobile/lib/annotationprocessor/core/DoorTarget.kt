package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.door.DoorDbType


enum class DoorTarget(val outputArgName: String, val supportedDbs: List<Int>) {
    JVM(AnnotationProcessorWrapper.OPTION_JVM_DIRS, listOf(DoorDbType.SQLITE, DoorDbType.POSTGRES)),
    ANDROID(AnnotationProcessorWrapper.OPTION_ANDROID_OUTPUT, listOf(DoorDbType.SQLITE)),
    JS(AnnotationProcessorWrapper.OPTION_JS_OUTPUT, listOf(DoorDbType.SQLITE))
}
