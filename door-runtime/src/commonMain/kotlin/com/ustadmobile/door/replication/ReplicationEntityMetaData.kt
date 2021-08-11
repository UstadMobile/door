package com.ustadmobile.door.replication

class ReplicationEntityMetaData(
    val tableId: Int,
    val entityTableName: String,
    val rtTableName: String,
    val receiveViewName: String,
    val entityPrimaryKeyFieldName: String,
    val entityIdVersionFieldName: String,
    val rtEntityForeignKeyFieldName: String,
    val rtDestNodeIdFieldName: String,
    val rtVersionFieldName: String,
    val rtProcessedFieldName: String,
    val entityFields: List<ReplicationFieldMetaData>,
    val trackerFields: List<ReplicationFieldMetaData>
) {

    val findPendingTrackerSql: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        SELECT $rtEntityForeignKeyFieldName AS primaryKey, 
               $rtVersionFieldName AS versionId
          FROM $rtTableName
         WHERE nodeId = ?
           AND CAST($rtProcessedFieldName AS INTEGER) = 0      
    """
    }

    val findAlreadyUpToDateEntitiesSql: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        SELECT $entityPrimaryKeyFieldName, AS primaryKey,
               $entityIdVersionFieldName AS versionId
          FROM $entityTableName
         WHERE $entityPrimaryKeyFieldName = ?
           AND $entityIdVersionFieldName = ?
        """
    }

    val updateSetTrackerProcessedSqlSqlite: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        UPDATE $rtTableName
           SET $rtProcessedFieldName = 1
         WHERE $rtEntityForeignKeyFieldName = ?
           AND $rtVersionFieldName = ?
           AND $rtDestNodeIdFieldName = ?     
        """
    }

    val findPendingReplicationSql: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        SELECT $entityTableName.*, $rtTableName.*
          FROM $rtTableName
               LEFT JOIN $entityTableName 
                    ON $rtTableName.$rtEntityForeignKeyFieldName = $entityTableName.$entityPrimaryKeyFieldName
         WHERE $rtTableName.$rtDestNodeIdFieldName = ?
           AND $rtProcessedFieldName = 0 
        """
    }
}