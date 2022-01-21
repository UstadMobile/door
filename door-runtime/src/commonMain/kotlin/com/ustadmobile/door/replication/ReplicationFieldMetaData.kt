package com.ustadmobile.door.replication

import kotlin.reflect.KClass

data class ReplicationFieldMetaData(
    val fieldName: String,
    val fieldType: Int //as per TypesKmp
) {
}