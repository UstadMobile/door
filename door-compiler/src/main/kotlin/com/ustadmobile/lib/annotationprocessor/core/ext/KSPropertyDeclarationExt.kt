package com.ustadmobile.lib.annotationprocessor.core.ext

import androidx.room.ColumnInfo
import com.google.devtools.ksp.symbol.KSPropertyDeclaration

/**
 * When this property represents a field on an entity, this provides the column name. If the name is specified using
 * ColumnInfo, this will be provided. Otherwise use the name of the field itself
 */
val KSPropertyDeclaration.entityPropColumnName: String
    get() {
        val colInfoName = getAnnotation(ColumnInfo::class)?.name
        return if(colInfoName != null && colInfoName != ColumnInfo.INHERIT_FIELD_NAME)
            colInfoName
        else
            simpleName.asString()
    }

val KSPropertyDeclaration.isTransient: Boolean
    get() = hasAnnotation(Transient::class)
