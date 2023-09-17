package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ext.executeUpdateAsync
import com.ustadmobile.door.jdbc.ext.useStatementAsync

/**
 * Create a temporary table for NodeEvent and add a Trigger to catch any new OutgoingReplications
 */
internal suspend fun Connection.createNodeEventTmpTableAndTrigger(
    hasOutgoingReplicationTable: Boolean
)  {
    createStatement().useStatementAsync { stmt ->
        stmt.executeUpdateAsync(NodeEventConstants.CREATE_NODE_EVENT_TMP_TABLE_SQL)
        stmt.takeIf { hasOutgoingReplicationTable }?.executeUpdateAsync(
            NodeEventConstants.CREATE_OUTGOING_REPLICATION_NODE_EVENT_TRIGGER
        )
    }
}
