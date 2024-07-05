package com.ustadmobile.door.annotation

/**
 * Most of the time HttpServerFunctionCall parameters will be automatically matched by name to the parameters that
 * are provided to the function that is marked @HttpAccessible itself. Sometimes it might be needed to use parameters
 * that are not specified within the arguments e.g. a literal, the requester node id, the paging key, and paging param.
 *
 * If an HttpServerFunctionParam has the same name a parameter on the function annotated @HttpAccessible itself,
 * the HttpServerFunctionParam will override it.
 */
@Suppress("unused") //Arguments are accessed as KSAnnotation, not unused
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class HttpServerFunctionParam(
    val name: String,
    val argType: ArgType,
    val literalValue: String = "",
    val fromName: String = "",
) {

    enum class ArgType {
        /**
         * Add a literal parameter to the function call. If there are
         */
        LITERAL,
        REQUESTER_NODE_ID,

        /**
         * Where this annotation is used on a function that returns a PagingSource, then this can be used to get the
         * paging key parameter that was sent.
         */
        PAGING_KEY,

        /**
         * Where this annotation is used on a function that returns a PagingSource, then this can be used to get the
         * paging source load size that was sent
         */
        PAGING_LOAD_SIZE,

        /**
         * Where this annotation is used on a function that returns a PagingSource, then this can be used to determine
         * the offset that is used for an offset/limit query. This uses DoorPagingUtil#getLimit.
         *
         * e.g. where a PagingSource returns certain rows, there might be a need to fetch data related to those rows.
         * The objective of using a PagingSource is to load data incrementally, so if one wants to load the related data
         * incrementally one might need to use SQL such as:
         *
         * SELECT RelatedData.*
         *  WHERE RelatedData.someKey IN
         *        (SELECT PagingData.someKey
         *           FROM PagingData
         *          WHERE condition
         *         OFFSET pagingOffset
         *          LIMIT pagingLimit)
         *
         * The number of rows of RelatedData to fetch like won't match the number of rows of PagingData.
         */
        PAGING_OFFSET,

        /**
         * Where this annotation is used on a function that returns a PagingSource, then this can be used to determine
         * the limit that is used for an offset/limit query. This uses DoorPagingUtil#getOffset. Useful as per
         * PAGING_OFFSET.
         */
        PAGING_LIMIT,

        /**
         * Where the target function uses a different name for the same parameter, the fromName can be specified
         * e.g. if the function with HttpAccessible annotation has a parameter called personId, and the
         * HttpServerFunction has a parameter called personUid, then one can use:
         *
         * HttpServerFunctionParam(name='personUid', argType = ArgType.MAP_OTHER_PARAM, fromName='personId')
         */
        MAP_OTHER_PARAM
    }


}