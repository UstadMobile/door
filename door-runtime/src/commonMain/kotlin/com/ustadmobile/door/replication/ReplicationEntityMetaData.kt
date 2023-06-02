package com.ustadmobile.door.replication

import com.ustadmobile.door.replication.ReplicationConstants.RECEIVE_VIEW_FROM_NODE_ID_FIELDNAME


class ReplicationEntityMetaData(
    val tableId: Int,
    val priority: Int,
    val entityTableName: String,
    val receiveViewName: String,
    val entityPrimaryKeyFieldName: String,
    val entityVersionIdFieldName: String,
    val entityFields: List<ReplicationFieldMetaData>,
    val attachmentUriField: String?,
    val attachmentMd5SumField: String?,
    val attachmentSizeField: String?,
    val batchSize: Int = 1000
) {

    val entityPrimaryKeyFieldType: Int by lazy(LazyThreadSafetyMode.NONE) {
        entityFields.first { it.fieldName == entityPrimaryKeyFieldName }.fieldType
    }

    val versionIdFieldType: Int by lazy(LazyThreadSafetyMode.NONE) {
        entityFields.first { it.fieldName == entityVersionIdFieldName }.fieldType
    }

    /**
     * Map of column name to column type for all fields.
     */
    internal val entityFieldsTypeMap: Map<String, Int>
        get() = entityFields.map { it.fieldName to it.fieldType }.toMap()


    val selectEntityByPrimaryKeysSql: String
        get() = """
            SELECT $entityTableName.* 
              FROM $entityTableName
             WHERE $entityTableName.$entityPrimaryKeyFieldName = ?
        """.trimIndent()



    val insertIntoReceiveViewSql: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        INSERT INTO $receiveViewName (${entityFields.joinToString{ it.fieldName }}, $RECEIVE_VIEW_FROM_NODE_ID_FIELDNAME)
               VALUES (${(0 .. (entityFields.size)).map { "?" }.joinToString()})
        """
    }


    companion object {
        const val KEY_PRIMARY_KEY = "primaryKey"

        const val KEY_VERSION_ID = "versionId"
    }
}