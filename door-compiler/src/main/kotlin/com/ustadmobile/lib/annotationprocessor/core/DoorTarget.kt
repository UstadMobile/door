package com.ustadmobile.lib.annotationprocessor.core

enum class DoorTarget(val outputArgName: String) {
    JVM(AnnotationProcessorWrapper.OPTION_JVM_DIRS),
    ANDROID(AnnotationProcessorWrapper.OPTION_ANDROID_OUTPUT),
    JS(AnnotationProcessorWrapper.OPTION_JS_OUTPUT)
}
