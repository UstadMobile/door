package com.ustadmobile.lib.annotationprocessor.core

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.ustadmobile.door.annotation.AttachmentUri
import com.ustadmobile.door.annotation.AttachmentMd5
import com.ustadmobile.door.annotation.AttachmentSize
import com.ustadmobile.lib.annotationprocessor.core.ext.hasAnnotation

/**
 * Used to represent the attachment-related fields on a database entity class.
 */
class EntityAttachmentInfo(ksClassDeclaration: KSClassDeclaration) {

    val uriPropertyName: String

    val md5PropertyName: String

    val sizePropertyName: String

    init {
        val allProperties = ksClassDeclaration.getAllProperties()
        uriPropertyName = allProperties
            .first { it.hasAnnotation(AttachmentUri::class) }.simpleName.asString()
        md5PropertyName = allProperties
            .first { it.hasAnnotation(AttachmentMd5::class) }.simpleName.asString()
        sizePropertyName = allProperties
            .first { it.hasAnnotation(AttachmentSize::class) }.simpleName.asString()
    }

}