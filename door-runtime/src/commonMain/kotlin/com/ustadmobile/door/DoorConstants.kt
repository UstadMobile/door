package com.ustadmobile.door

object DoorConstants {

    const val MIME_TYPE_PLAIN = "text/plain"

    const val MIME_TYPE_JSON = "application/json"

    const val HEADER_DBVERSION = "door-dbversion"

    /**
     * Header that should contain the node id and auth in the form of nodeId/auth e.g. 1234/secret
     */
    const val HEADER_NODE_AND_AUTH = "door-node"

    /**
     * Header on some responses (including data pull) that contains the nodeid that sent the response
     */
    const val HEADER_NODE_ID = "door-node-id"

    const val DBINFO_TABLENAME = "_doorwayinfo"

    const val PGSECTION_COMMENT_PREFIX = "/*psql"

    const val NOTPGSECTION_COMMENT_PREFIX = "--notpsql"

    const val NOTPGSECTION_END_COMMENT_PREFIX = "--endnotpsql"

    const val RECEIVE_VIEW_SUFFIX = "_ReceiveView"

    /**
     * Header used on http responses for PagingSource using PULL_REPLICATE_ENTITIES to provide
     * endOfPaginationReached for DoorRepositoryRemoteMediator
     */
    const val HEADER_PAGING_END_REACHED = "door-paging-end-reached"

}