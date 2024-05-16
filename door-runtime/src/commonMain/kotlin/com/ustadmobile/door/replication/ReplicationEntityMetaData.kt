package com.ustadmobile.door.replication

import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.door.annotation.Trigger
import com.ustadmobile.door.replication.ReplicationConstants.RECEIVE_VIEW_FROM_NODE_ID_FIELDNAME


class ReplicationEntityMetaData(
    val tableId: Int,
    val entityTableName: String,
    val receiveViewName: String,
    val entityPrimaryKeyFieldNames: List<String>,
    val entityVersionIdFieldName: String,
    val entityFields: List<ReplicationFieldMetaData>,
    val batchSize: Int = 1000,
    val remoteInsertStrategy: ReplicateEntity.RemoteInsertStrategy,
    val triggers: List<Trigger>,
) {

    /**
     * Map of column name to column type for all fields.
     */
    internal val entityFieldsTypeMap: Map<String, Int>
        get() = entityFields.map { it.fieldName to it.fieldType }.toMap()


    val selectEntityByPrimaryKeysSql: String
        get() = """
            SELECT $entityTableName.* 
              FROM $entityTableName
             WHERE ${entityPrimaryKeyFieldNames.joinToString(separator = " AND ") { "$it = ?" } }
        """.trimIndent()



    val insertIntoReceiveViewSql: String by lazy(LazyThreadSafetyMode.NONE) {
        """
        INSERT INTO $receiveViewName (${entityFields.joinToString{ it.fieldName }}, $RECEIVE_VIEW_FROM_NODE_ID_FIELDNAME)
               VALUES (${(0 .. (entityFields.size)).map { "?" }.joinToString()})
        """
    }

    /**
     * The SQL that will be used to create the Receive View (used by triggers as per ReplicateEntity strategy.
     */
    internal val createReceiveViewSql: String
        get() = """
                CREATE VIEW $receiveViewName AS 
                       SELECT $entityTableName.*, 
                              CAST(0 AS BIGINT) AS fromNodeId
                         FROM $entityTableName
            """


}