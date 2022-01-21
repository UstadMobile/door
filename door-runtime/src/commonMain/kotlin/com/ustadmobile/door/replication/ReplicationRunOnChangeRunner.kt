package com.ustadmobile.door.replication

/**
 * This class is extended by a generated implementation for each database.
 *
 */
interface ReplicationRunOnChangeRunner {

    /**
     * The generated implementation will call functions that have been annotated with @ReplicationRunOnChange that
     * need to be run for the given list of tables that have been changed.
     *
     * @return a list of the tables for which pending notifications should be checked (e.g. those specified by
     * checkPendingReplicationsFor on the @ReplicationRunOnChange annotation)
     *  NOTE: it might be worth changing this to a data class so that other info could be returned if needed.
     */
    suspend fun runReplicationRunOnChange(tableNames: Set<String>): Set<String>

    suspend fun runOnNewNode(newNodeId: Long): Set<String>

}