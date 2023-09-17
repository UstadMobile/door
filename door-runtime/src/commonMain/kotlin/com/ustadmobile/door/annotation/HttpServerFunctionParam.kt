package com.ustadmobile.door.annotation

/**
 * Most of the time HttpServerFunctionCall parameters will be automatically matched by name to the parameters that
 * are provided to the function that is marked @HttpAccessible itself. Sometimes it might be needed to use parameters
 * that are not specified within the arguments e.g. a literal, the requester node id, the paging key, and paging param.
 *
 * If an HttpServerFunctionParam has the same name a parameter on the function annotated @HttpAccessible itself,
 * the HttpServerFunctionParam will override it.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class HttpServerFunctionParam(
    val name: String,
    val argType: ArgType,
    val literalValue: String = "",
) {

    enum class ArgType {
        /**
         * Add a literal parameter to the function call. If there are
         */
        LITERAL,
        REQUESTER_NODE_ID, PAGING_KEY, PAGING_LOAD_SIZE
    }


}