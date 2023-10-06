package com.ustadmobile.door

import com.ustadmobile.door.log.DoorLogger
import io.ktor.client.*
import kotlinx.serialization.json.Json

/**
 * Contains the configuration for a repository. It is created via a platform-specific builder that may have additional
 * dependencies on specific platforms.
 */
expect class RepositoryConfig {

    val context: Any

    val endpoint: String

    val httpClient: HttpClient

    val json: Json

    /**
     * The nodeId for the local node (not the remote node - which is only discovered after connecting to it). This
     * will match the single row that is in SyncNode.
     */
    val nodeId: Long

    /**
     * Random auth string known only to the repository server and the device
     */
    val auth: String

    val dbName: String

    val logger: DoorLogger

}