package com.ustadmobile.lib.annotationprocessor.core

import com.squareup.kotlinpoet.*
import com.ustadmobile.door.annotation.ReplicateEntity
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement


/**
 * Convenience shorthand for creating a new classname with the given suffix and the same package
 * as the original
 */
fun ClassName.withSuffix(suffix: String) = ClassName(this.packageName, "$simpleName$suffix")


