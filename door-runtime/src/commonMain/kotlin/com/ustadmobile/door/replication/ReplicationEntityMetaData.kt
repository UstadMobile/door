package com.ustadmobile.door.replication

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

    val findPendingTrackerSql: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        SELECT $trackerForeignKeyFieldName AS primaryKey, 
               $trackerVersionFieldName AS versionId
          FROM $trackerTableName
         WHERE nodeId = ?
           AND CAST($trackerProcessedFieldName AS INTEGER) = 0      
    """
    }

    val findAlreadyUpToDateEntitiesSql: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        SELECT $entityPrimaryKeyFieldName, AS primaryKey,
               $entityVersionIdFieldName AS versionId
          FROM $entityTableName
         WHERE $entityPrimaryKeyFieldName = ?
           AND $entityVersionIdFieldName = ?
        """
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
}