package com.ustadmobile.door

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD

/**
 * This is a convenience wrapper class that is used with NanoHTTPD to make an object that is then
 * used with the DI as context.
 */
class NanoHttpdCall(val uriResource: RouterNanoHTTPD.UriResource, val urlParams: Map<String, String>, val session: NanoHTTPD.IHTTPSession) {
}