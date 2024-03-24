package com.ustadmobile.door.paging

import com.ustadmobile.door.DoorConstants
import io.ktor.client.statement.*

/**
 * Where the receiver HttpResponse is the response for a PagingSource which uses PULL_REPLICATE_ENTITIES, gets
 * endOfPaginationReached from the response using the header.
 */
fun HttpResponse.endOfPaginationReached(): Boolean {
    return headers[DoorConstants.HEADER_PAGING_END_REACHED]?.toBoolean() ?: true
}
