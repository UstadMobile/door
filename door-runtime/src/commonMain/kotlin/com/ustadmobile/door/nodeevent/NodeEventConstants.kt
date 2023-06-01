package com.ustadmobile.door.nodeevent

object NodeEventConstants {

    const val CREATE_EVENT_TMP_TABLE_SQL = """
            CREATE TEMP TABLE IF NOT EXISTS NodeEvent (
                   eventId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                   what INTEGER NOT NULL, 
                   toNode BIGINT NOT NULL,
                   tableId INTEGER NOT NULL,
                   key1 BIGINT NOT NULL,
                   key2 BIGINT NOT NULL
            )
        """

    const val CREATE_OUTGOING_REPLICATION_EVENT_TRIGGER = """
            CREATE TEMP TRIGGER IF NOT EXISTS _door_event_trig
             AFTER INSERT
                ON OutgoingReplication
             BEGIN INSERT INTO NodeEvent(what, toNode, tableId, key1, key2)
                   VALUES (${NodeEventMessage.WHAT_REPLICATION}, 
                           NEW.destNodeId, 
                           NEW.orTableId,
                           NEW.orPk1,
                           NEW.orPk2
                          );
                   END      
        """

    const val SELECT_EVENT_FROM_TMP_TABLE = """
        SELECT NodeEvent.what AS what,
               NodeEvent.toNode AS toNode,
               NodeEvent.tableId AS tableId,
               NodeEvent.key1 AS key1,
               NodeEvent.key2  AS key2
          FROM NodeEvent
    """


}