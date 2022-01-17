package com.ustadmobile.door.attachments

import com.ustadmobile.door.ext.jsonNullableString
import com.ustadmobile.door.replication.ReplicationEntityMetaData
import kotlinx.serialization.json.*

class JsonEntityWithAttachment(
    private val jsonObject: JsonObject,
    private val replicationEntityMetaData: ReplicationEntityMetaData
) : EntityWithAttachment {

    private val attachmentUriFieldName: String
        get() = replicationEntityMetaData.attachmentUriField ?: throw IllegalArgumentException("no uri!")

    private val attachmentMd5FieldName: String
        get() = replicationEntityMetaData.attachmentMd5SumField ?: throw IllegalArgumentException("no md5 field!")

    private val attachmentSizefieldName: String
        get() = replicationEntityMetaData.attachmentSizeField ?: throw IllegalArgumentException("no attachment size field!")

    override var attachmentUri: String?
        get() = jsonObject.get(attachmentUriFieldName)?.jsonNullableString
        set(value) {
            throw IllegalStateException("JsonEntityWithAttachment is read only!")
        }

    override var attachmentMd5: String?
        get() =jsonObject.get(attachmentMd5FieldName)?.jsonPrimitive?.jsonNullableString
        set(value) {
            throw IllegalStateException("JsonEntityWithAttachment is read only!")
        }

    override var attachmentSize: Int
        get() = jsonObject.get(attachmentSizefieldName)?.jsonPrimitive?.intOrNull ?: 0
        set(value) {
            throw IllegalStateException("JsonEntityWithAttachment is read only!")
        }

    override val tableName: String
        get() = replicationEntityMetaData.entityTableName
}