package com.ustadmobile.lib.annotationprocessor.core

import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.ustadmobile.door.DoorDbType
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * Shorthand to convert the given TypeMirror to a TypeElement using processingEnv if it represents
 * a TypeElement (e.g. a class)
 */
fun TypeMirror.asTypeElement(processingEnv: ProcessingEnvironment): TypeElement? =
        processingEnv.typeUtils.asElement(this) as? TypeElement

/**
 * Unwrap the component type if this type mirror represents an array or a list. Otherwise return
 * the type mirror itself
 */
fun TypeMirror.unwrapListOrArrayComponentType(processingEnv: ProcessingEnvironment): TypeMirror =
        if(this.kind == TypeKind.ARRAY) {
            (this as ArrayType).componentType
        }else if(this.kind == TypeKind.DECLARED && this is DeclaredType &&
                this.asElement() == processingEnv.elementUtils.getTypeElement("java.util.List")) {
            this.typeArguments[0]
        }else {
            this
        }

/**
 * Where this TypeMirror represents a primitive or a string, get the SQL type name (e.g. BIGINT, VARCHAR, etc)
 */
fun TypeMirror.toSqlType(
    dbType: Int,
    processingEnv: ProcessingEnvironment,
): String  {
    if(kind == TypeKind.DECLARED && (this as DeclaredType).asElement().simpleName.toString() == "String")
        return "TEXT"

    val primType = if(this is DeclaredType) {
        processingEnv.typeUtils.unboxedType(this)
    }else {
        this
    }

    return when {
        dbType == DoorDbType.SQLITE &&
                primType.kind in listOf(TypeKind.BOOLEAN, TypeKind.BYTE, TypeKind.SHORT, TypeKind.INT, TypeKind.LONG) -> "INTEGER"
        dbType == DoorDbType.SQLITE && primType.kind in listOf(TypeKind.DOUBLE, TypeKind.FLOAT) -> "REAL"


        primType.kind == TypeKind.BOOLEAN -> "BOOL"
        primType.kind == TypeKind.BYTE -> "SMALLINT"
        primType.kind == TypeKind.SHORT -> "SMALLINT"
        primType.kind == TypeKind.INT -> "INTEGER"
        primType.kind == TypeKind.LONG -> "BIGINT"
        primType.kind == TypeKind.FLOAT -> "FLOAT"
        primType.kind == TypeKind.DOUBLE -> "DOUBLE PRECISION"

        else -> throw IllegalArgumentException("$this toSqlType: unsupported type")
    }
}

