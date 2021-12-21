package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.ext.getOrThrow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

class ReplicationEntityMetaData(
    val tableId: Int,
    val priority: Int,
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
    val trackerFields: List<ReplicationFieldMetaData>,
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
        get() = entityFields.map { it.fieldName to it.fieldType }.toMap() + trackerFields.map { it.fieldName to it.fieldType }

    internal val insertIntoReceiveViewTypesList: List<Int>
        get() = entityFields.map { it.fieldType } + trackerFields.map { it.fieldType }

    internal val insertIntoReceiveViewTypeColNames: List<String>
        get() = entityFields.map { it.fieldName } + trackerFields.map { it.fieldName }

    val findPendingTrackerSql: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        SELECT $trackerForeignKeyFieldName AS primaryKey, 
               $trackerVersionFieldName AS versionId
          FROM $trackerTableName
         WHERE $trackerDestNodeIdFieldName = ?
           AND CAST($trackerProcessedFieldName AS INTEGER) = 0
         LIMIT $batchSize 
        OFFSET ?
    """
    }

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
           AND CAST($trackerProcessedFieldName AS INTEGER) = 0 
         LIMIT $batchSize  
        """
    }

    val insertIntoReceiveViewSql: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        INSERT INTO $receiveViewName (${entityFields.joinToString{ it.fieldName }}, ${trackerFields.joinToString { it.fieldName }})
               VALUES (${(0 until (entityFields.size + trackerFields.size)).map { "?" }.joinToString()})
        """
    }

    /**
     * Converts a JsonObject representing the actual entity itself to a Replication Tracker Summary, which has only the
     * primary key and version id. The field names of the Replication Tracker Summary are fixed as per KEY_PRIMARY_KEY
     * and KEY_VERSION_ID.
     */
    internal fun entityToReplicationTrackerSummary(entityObject: JsonObject): JsonObject {
        return JsonObject(mapOf(
            KEY_PRIMARY_KEY to entityObject.getOrThrow(entityPrimaryKeyFieldName),
            KEY_VERSION_ID to entityObject.getOrThrow(entityVersionIdFieldName)))
    }

    /**
     * Converts a JsonArray (which must contain json representing the entity itself) to an array of Replication Tracker
     * Summary objects as per entityToReplicationTrackerSummary
     */
    internal fun entityJsonArrayToReplicationTrackSummaryArray(entityArray: JsonArray): JsonArray {
        return JsonArray(entityArray.map { entityToReplicationTrackerSummary(it as JsonObject) })
    }

    companion object {
        const val KEY_PRIMARY_KEY = "primaryKey"

        const val KEY_VERSION_ID = "versionId"
    }
}