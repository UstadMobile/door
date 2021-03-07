package com.ustadmobile.lib.annotationprocessor.core

import javax.lang.model.element.TypeElement
import com.ustadmobile.door.annotation.AttachmentUri
import com.ustadmobile.door.annotation.AttachmentMd5
import com.ustadmobile.door.annotation.AttachmentSize

/**
 * Used to represent the attachment-related fields on a database entity class.
 */
class EntityAttachmentInfo {

    val uriPropertyName: String

    val md5PropertyName: String

    val sizePropertyName: String

    constructor(typeElement: TypeElement) {
        uriPropertyName = typeElement.enclosedElements
                .first { it.hasAnnotation(AttachmentUri::class.java) }.simpleName.toString()
        md5PropertyName = typeElement.enclosedElements
                .first { it.hasAnnotation(AttachmentMd5::class.java) }.simpleName.toString()
        sizePropertyName = typeElement.enclosedElements
                .first { it.hasAnnotation(AttachmentSize::class.java) }.simpleName.toString()
    }

}