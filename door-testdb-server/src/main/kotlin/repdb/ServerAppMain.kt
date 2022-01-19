package repdb

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.netty.handler.codec.http.HttpServerCodec

class ServerAppMain {

    companion object {

        const val MAX_INITIAL_LINE_LENGTH = 32 * 1024

        const val MAX_HEADER_SIZE = 4096

        const val MAX_CHUNK_SIZE = 4096

        @JvmStatic
        fun main(args: Array<String>) {
            embeddedServer(Netty, commandLineEnvironment(args)) {
                requestReadTimeoutSeconds = 600
                responseWriteTimeoutSeconds = 600
                httpServerCodec= {
                    HttpServerCodec(MAX_INITIAL_LINE_LENGTH, MAX_HEADER_SIZE, MAX_CHUNK_SIZE)
                }
            }.start(true)
        }

    }
}