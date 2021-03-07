package com.ustadmobile.lib.annotationprocessor.core

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

class ExampleDatabase2MockDispatcher : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        return when {
            request.path.startsWith("/ExampleDatabase2/ExampleDao2/insertAsyncAndGiveId")-> {
                val response = MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json; charset=UTF-8")
                        .setHeader("Access-Control-Allow-Origin", "*")
                        .setHeader("Access-Control-Allow-Headers",
                                "Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With")
                if(request.method.equals("POST", ignoreCase = true)) {
                    response.setBody("42")
                }

                response
            }


            else ->
                MockResponse().setResponseCode(404)
                    .setBody("Not found")
                    .setHeader("Content-Type", "text/plain")
        }
    }
}

fun main(args: Array<String>) {
    val mockServer = MockWebServer()
    mockServer.setDispatcher(ExampleDatabase2MockDispatcher())
    mockServer.start(8087)
    while(true) {
        Thread.sleep(1000)
    }
    mockServer.shutdown()
}


