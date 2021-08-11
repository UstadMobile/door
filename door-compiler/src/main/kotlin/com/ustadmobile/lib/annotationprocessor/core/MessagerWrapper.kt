package com.ustadmobile.lib.annotationprocessor.core

import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.tools.Diagnostic

/**
 * Simple wrapper for Messager that can be used to determine if any errors have been emitted
 */
class MessagerWrapper(private val messageDest: Messager) : Messager {

    var hasError = false
        private set

    override fun printMessage(p0: Diagnostic.Kind?, p1: CharSequence?) {
        hasError = hasError || (p0 == Diagnostic.Kind.ERROR)
        messageDest.printMessage(p0, p1)
    }

    override fun printMessage(p0: Diagnostic.Kind?, p1: CharSequence?, p2: Element?) {
        hasError = hasError || (p0 == Diagnostic.Kind.ERROR)
        messageDest.printMessage(p0, p1, p2)
    }

    override fun printMessage(p0: Diagnostic.Kind?, p1: CharSequence?, p2: Element?, p3: AnnotationMirror?) {
        hasError = hasError || (p0 == Diagnostic.Kind.ERROR)
        messageDest.printMessage(p0, p1, p2, p3)
    }

    override fun printMessage(
        p0: Diagnostic.Kind?,
        p1: CharSequence?,
        p2: Element?,
        p3: AnnotationMirror?,
        p4: AnnotationValue?
    ) {
        hasError = hasError || (p0 == Diagnostic.Kind.ERROR)
        messageDest.printMessage(p0, p1, p2, p3, p4)
    }
}