package com.ustadmobile.door.nodeevent


import com.ustadmobile.door.message.DoorMessage

object NodeEventConstants {

    const val OUTGOING_REPLICATION_TABLE_NAME = "OutgoingReplication"

    private const val NODE_EVENT_TABLE_SQL = """
        TABLE IF NOT EXISTS NodeEvent (
                   eventId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                   what INTEGER NOT NULL, 
                   toNode BIGINT NOT NULL,
                   tableId INTEGER NOT NULL,
                   key1 BIGINT NOT NULL,
                   key2 BIGINT NOT NULL
            )
    """

    const val CREATE_NODE_EVENT_TMP_TABLE_SQL = """
            CREATE TEMP $NODE_EVENT_TABLE_SQL
        """

    const val CREATE_NODE_EVENT_TABLE_SQL = """
        CREATE $NODE_EVENT_TABLE_SQL
    """

    private const val OUTGOING_REPLICATION_NODE_EVENT_TRIGGER = """
        TRIGGER IF NOT EXISTS _door_event_trig
             AFTER INSERT
                ON $OUTGOING_REPLICATION_TABLE_NAME
             BEGIN INSERT INTO NodeEvent(what, toNode, tableId, key1, key2)
                   VALUES (${DoorMessage.WHAT_REPLICATION_PUSH}, 
                           NEW.destNodeId, 
                           NEW.orTableId,
                           NEW.orPk1,
                           NEW.orPk2
                          );
                   END      
    """

    const val CREATE_OUTGOING_REPLICATION_NODE_EVENT_TRIGGER_TMP = """
            CREATE TEMP $OUTGOING_REPLICATION_NODE_EVENT_TRIGGER
        """

    const val CREATE_OUTGOING_REPLICATION_NODE_EVENT_TRIGGER = """
        CREATE $OUTGOING_REPLICATION_NODE_EVENT_TRIGGER
    """

    const val SELECT_EVENT_FROM_TMP_TABLE = """
        SELECT NodeEvent.what AS what,
               NodeEvent.toNode AS toNode,
               NodeEvent.tableId AS tableId,
               NodeEvent.key1 AS key1,
               NodeEvent.key2  AS key2
          FROM NodeEvent
    """


    const val CLEAR_EVENTS_TMP_TABLE = """
        DELETE FROM NodeEvent
    """

}