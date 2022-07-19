package com.ustadmobile.lib.annotationprocessor.core.ext

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias

//Based on https://kotlinlang.org/docs/ksp-examples.html#get-all-member-functions
fun KSTypeAlias.findActualTypeClassDecl(): KSClassDeclaration {
    return findActualType().declaration as KSClassDeclaration
}

fun KSTypeAlias.findActualType() : KSType {
    val resolvedType = this.type.resolve()
    return if(resolvedType is KSTypeAlias) {
        resolvedType.findActualType()
    }else {
        resolvedType
    }
}
