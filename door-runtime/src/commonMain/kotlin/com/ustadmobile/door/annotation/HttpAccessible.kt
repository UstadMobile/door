package com.ustadmobile.door.annotation

/**
 * Indicates that the given function will have an endpoint that will be accessible by http. The endpoint will respond to
 * get if all parameters are primitives/strings and/or arrays thereof. If any parameter is an object, it will be a post
 * endpoint (where the body of the request should be the object in json form).
 *
 * @param clientStrategy - the strategy that the generated repository client (created by .asRepository(..) and
 *        generated endpoints will use.
 * @param httpMethod - the HTTP method (GET or POST) that will be used by the generated repository client (created by
 *        .asRepository(..)) and generated http endpoints will use.
 *
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class HttpAccessible(
    val clientStrategy: ClientStrategy = ClientStrategy.AUTO,
    val httpMethod: HttpMethod = HttpMethod.AUTO,
    val pullQueriesToReplicate: Array<HttpServerFunctionCall> = arrayOf(),
) {

    enum class ClientStrategy {
        /**
         * If the return type includes any ReplicateEntities (via inheritance or @Embedded properties) then use
         * PULL_REPLICATE_ENTITIES strategy, otherwise use the HTTP_WITH_FALLBACK strategy.
         */
        AUTO,

        /**
         * The server will return a list of all ReplicateEntities that are part of the result (via inheritence or
         * @Embedded properties) of the query function itself and any extra functions specified via
         * pullQueriesToReplicate. The entities will be inserted into the local database on the client the normal way
         * (e.g. as specified on the @ReplicateEntity annotation). After any entities received from the server have
         * been inserted the query will be run locally. If the server is not accessible, then the query will be run
         * locally.
         */
        PULL_REPLICATE_ENTITIES,

        /**
         * The server will run the query as specified directly and respond with the result of the query as Json. The
         * client will return the result as delivered from the server. The local database of the client will not be
         * queried. If the server is not reachable an exception will be thrown.
         */
        HTTP_OR_THROW,

        /**
         * As per HTTP_ONLY, however, if the server is not reachable, then the query will be run locally
         */
        HTTP_WITH_FALLBACK,

        /**
         * The client will only query the local database. Normally in this case there would be no need to mark a
         * DAO function as HttpAccessible, however this can be done, and then the rest endpoint can be used via HTTP
         * by any other client if desired.
         */
        LOCAL_DB_ONLY
    }

    enum class HttpMethod {
        /**
         *  When AUTO is used (or applied by default), then Http POST will be used if any function parameter is annotated
         *  @RequestBody , otherwise GET will be used
         */
        AUTO,

        /**
         * Explicitly use http get. This is not allowed if there is a request body param.
         */
        GET,

        /**
         * Explicitly use http post. This can be used even if there is no http http request body param.
         */
        POST
    }

}
