package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias

fun KSType.unwrapComponentTypeIfListOrArray(
    resolver: Resolver
): KSType {
    return if(isListOrArrayType(resolver)) {
        this.arguments.first().type?.resolve()
            ?: throw IllegalArgumentException("unwrapComponentTypeIfListOrArray: List or array type cannot be resolved!")
    }else {
        this
    }

}

fun KSType.isListOrArrayType(
    resolver: Resolver
): Boolean {
    return (this == resolver.builtIns.arrayType)
            || ((this.declaration as? KSClassDeclaration)?.isListDeclaration() == true)
}

fun KSType.resolveIfAlias(): KSType {
    return if(this is KSTypeAlias) {
        findActualType()
    }else {
        this
    }
}