package com.ustadmobile.door.http

data class DoorJsonResponse(
    val bodyText: String,
    val responseCode: Int = 200,
    val contentType: String = "application/json",
    val headers: List<Pair<String, String>> = emptyList(),
) {



}