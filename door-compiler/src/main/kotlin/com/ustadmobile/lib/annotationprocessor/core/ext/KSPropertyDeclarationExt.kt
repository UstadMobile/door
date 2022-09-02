package com.ustadmobile.lib.annotationprocessor.core.ext

import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.ksp.toKModifier
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ustadmobile.door.annotation.DoorPrimaryAutoGenerateKeyField

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

val KSPropertyDeclaration.isIgnored: Boolean
    get() = hasAnyAnnotation(Transient::class, Ignore::class) || getter?.hasAnnotation(Ignore::class) ?:
            setter?.hasAnnotation(Ignore::class) ?: false


fun KSPropertyDeclaration.toPropSpecBuilder(
    containingType: KSType,
) :PropertySpec.Builder {
    return PropertySpec.builder(simpleName.asString(), asMemberOf(containingType).toTypeName())
        .addModifiers(modifiers.mapNotNull { it.toKModifier() })

}

val KSPropertyDeclaration.isEntityAutoGeneratePrimaryKey: Boolean
    get() {
        return when {
            getAnnotation(PrimaryKey::class)?.autoGenerate == true -> true
            (this.parent as? KSClassDeclaration)?.getAnnotation(DoorPrimaryAutoGenerateKeyField::class)?.value == simpleName.asString() -> true
            else -> false
        }

    }
