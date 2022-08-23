package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * Shorthand to wrap the toTypeName operation in a try/catch. toTypeName throws an exception if this is an unresolved
 * or error type, which will then crash KSP and stop the user from seeing the actual compiler error.
 */
fun KSTypeReference.toTypeNameOrNullIfError(
    ksNode: KSNode,
    logger: KSPLogger
) : TypeName?{
    return try {
        toTypeName()
    }catch(e: Exception) {
        logger.error("Error resolving type name", ksNode)
        null
    }
}
