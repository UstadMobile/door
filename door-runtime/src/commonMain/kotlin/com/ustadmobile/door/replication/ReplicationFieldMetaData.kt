package com.ustadmobile.door.replication

/**
 * @param dbFieldType constant as per TypesKmp
 * @param nullable
 */
data class ReplicationFieldMetaData(
    override val fieldName: String,
    override val dbFieldType: Int,
    override val nullable: Boolean,
): JsonDbFieldInfo
