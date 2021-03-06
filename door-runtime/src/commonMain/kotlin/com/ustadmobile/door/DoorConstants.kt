package com.ustadmobile.door

object DoorConstants {

    val MIME_TYPE_PLAIN = "text/plain"

    val MIME_TYPE_JSON = "application/json"

    val HEADER_DBVERSION = "door-dbversion"

    /**
     * Header that should contain the node id and auth in the form of nodeId/auth e.g. 1234/secret
     */
    val HEADER_NODE = "door-node"

}