package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDbType

class ReplicationEntityMetaData(
    val tableId: Int,
    val entityTableName: String,
    val trackerTableName: String,
    val receiveViewName: String,
    val entityPrimaryKeyFieldName: String,
    val entityVersionIdFieldName: String,
    val trackerForeignKeyFieldName: String,
    val trackerDestNodeIdFieldName: String,
    val trackerVersionFieldName: String,
    val trackerProcessedFieldName: String,
    val entityFields: List<ReplicationFieldMetaData>,
    val trackerFields: List<ReplicationFieldMetaData>
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

    val findPendingTrackerSql: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        SELECT $trackerForeignKeyFieldName AS primaryKey, 
               $trackerVersionFieldName AS versionId
          FROM $trackerTableName
         WHERE $trackerDestNodeIdFieldName = ?
           AND CAST($trackerProcessedFieldName AS INTEGER) = 0      
    """
    }

    val findAlreadyUpToDateEntitiesSql: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        SELECT $entityPrimaryKeyFieldName AS $KEY_PRIMARY_KEY,
               $entityVersionIdFieldName AS $KEY_VERSION_ID
          FROM $entityTableName
         WHERE $entityPrimaryKeyFieldName = ?
           AND $entityVersionIdFieldName = ?
        """
    }

    fun updateSetTrackerProcessedSql(dbType: Int) = if(dbType == DoorDbType.SQLITE) {
        updateSetTrackerProcessedSqlSqlite
    }else {
        updateSetTrackerProcessedSqlPostgres
    }

    val updateSetTrackerProcessedSqlSqlite: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        UPDATE $trackerTableName
           SET $trackerProcessedFieldName = 1
         WHERE $trackerForeignKeyFieldName = ?
           AND $trackerVersionFieldName = ?
           AND $trackerDestNodeIdFieldName = ?     
        """
    }

    val updateSetTrackerProcessedSqlPostgres: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        UPDATE $trackerTableName
           SET $trackerProcessedFieldName = true
         WHERE $trackerForeignKeyFieldName = ?
           AND $trackerVersionFieldName = ?
           AND $trackerDestNodeIdFieldName = ?     
        """
    }

    val findPendingReplicationSql: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        SELECT $entityTableName.*, $trackerTableName.*
          FROM $trackerTableName
               LEFT JOIN $entityTableName 
                    ON $trackerTableName.$trackerForeignKeyFieldName = $entityTableName.$entityPrimaryKeyFieldName
         WHERE $trackerTableName.$trackerDestNodeIdFieldName = ?
           AND $trackerProcessedFieldName = 0 
        """
    }

    companion object {
        const val KEY_PRIMARY_KEY = "primaryKey"

        const val KEY_VERSION_ID = "versionId"
    }
}