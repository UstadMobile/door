package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Query
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.util.*
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType
import org.jetbrains.annotations.Nullable
import javax.lang.model.element.Modifier
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeKind

/**
 * An ExecutableElement could be a normal Kotlin function, or it could be a getter method. If it is
 * a Java getter method, then we should access it as .propertyName , otherwise it should be accessed
 * as functionName()
 */
internal fun ExecutableElement.makeAccessorCodeBlock(): CodeBlock {
    val codeBlock = CodeBlock.builder()
    if(this.simpleName.toString().startsWith("get")) {
        codeBlock.add(simpleName.substring(3, 4).lowercase() + simpleName.substring(4))
    }else {
        codeBlock.add("$simpleName()")
    }

    return codeBlock.build()
}

fun ExecutableElement.hasAnyListOrArrayParams(): Boolean {
    return parameters.any {
        it.asType().kind == TypeKind.DECLARED  &&
                ((it.asType() as DeclaredType).asElement() as TypeElement).qualifiedName.toString() == "java.util.List"
    }
}
