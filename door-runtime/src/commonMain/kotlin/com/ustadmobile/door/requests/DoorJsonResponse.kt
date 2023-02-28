package com.ustadmobile.door.requests

/**
 * Simple data class that is used as a common representation of a json http response. This can then be sent using KTOR
 * over HTTP, Android Interprocess (IPC) Messenger Service, or Bluetooth.
 */
data class DoorJsonResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val responseBody: String?
) {
}