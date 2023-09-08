package com.ustadmobile.door.http

import kotlinx.serialization.json.Json

/**
 * Similar to RepoConfig - will contain any dependencies for the server, and config to control incoming replication
 * accept/reject etc.
 */
data class DoorHttpServerConfig(
    val json: Json
) {
}