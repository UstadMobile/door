package com.ustadmobile.lib.annotationprocessor.core

/**
 * Convenience extension
 */
inline fun <T> T.applyIf(condition: Boolean, block : T.() -> Unit) : T = apply {
    if(condition)
        block(this)
}
