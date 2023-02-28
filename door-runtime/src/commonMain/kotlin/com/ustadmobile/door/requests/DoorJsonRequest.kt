package com.ustadmobile.door.requests

/**
 * Simple data class that is used as a common representation of a json http request. This can then be sent using KTOR
 * over HTTP, Android Interprocess (IPC) Messenger Service, or Bluetooth.
 *
 * An extension function will be generated for each database function that will create a request object roughly as follows:
 *
 * fun DaoClass.functionName_JsonRequest(repoConfig, param1: String, param2) {
 *     return DoorJsonRequest(
 *         method = "GET",
 *         protocol = "http",
 *         path = "repoConfig.path/DaoName/functionName",
 *         queryParams = mapOf("param1" to param1,
 *                              "param2" to param2),
 *     )
 * }
 *
 * @param queryParams map of query parameters to send. Simplified by design not to allow duplicate keys
 * @param headers map of headers to send. Simplified by design not to allow duplicate keys
 */
data class DoorJsonRequest(
    val method: Method,
    val protocol: String,
    val host: String,
    val path: String,
    val queryParams: Map<String, String>,
    val headers: Map<String, String>,
    val requestBody: String? = null,
){

    enum class Method {
        GET, POST, PUT
    }

}