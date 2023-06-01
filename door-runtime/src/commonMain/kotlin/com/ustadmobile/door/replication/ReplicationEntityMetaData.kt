package com.ustadmobile.door.replication


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

    internal val pendingReplicationFieldTypesMap: Map<String, Int>
        get() = mapOf(KEY_PRIMARY_KEY to entityPrimaryKeyFieldType,
            KEY_VERSION_ID to versionIdFieldType)

    /**
     * Map of column name to column type for rows returned by the findPendingReplicationSql query.
     */
    internal val pendingReplicationColumnTypesMap: Map<String, Int>
        get() = entityFields.map { it.fieldName to it.fieldType }.toMap()

    internal val insertIntoReceiveViewTypesList: List<Int>
        get() = entityFields.map { it.fieldType }
    internal val insertIntoReceiveViewTypeColNames: List<String>
        get() = entityFields.map { it.fieldName }


    val findAlreadyUpToDateEntitiesSql: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        SELECT $entityPrimaryKeyFieldName AS $KEY_PRIMARY_KEY,
               $entityVersionIdFieldName AS $KEY_VERSION_ID
          FROM $entityTableName
         WHERE $entityPrimaryKeyFieldName = ?
           AND $entityVersionIdFieldName = ?
         LIMIT $batchSize 
        """
    }


    val insertIntoReceiveViewSql: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        INSERT INTO $receiveViewName (${entityFields.joinToString{ it.fieldName }})
               VALUES (${(0 until (entityFields.size)).map { "?" }.joinToString()})
        """
    }


    companion object {
        const val KEY_PRIMARY_KEY = "primaryKey"

        const val KEY_VERSION_ID = "versionId"
    }
}