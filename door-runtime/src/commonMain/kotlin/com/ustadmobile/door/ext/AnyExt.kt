package com.ustadmobile.door.ext

/**
 * A function that is designed ot generate an instance identifier. This is System.identityHashCode
 * on JVM. TBD on Javascript - could be something like
 * https://stackoverflow.com/questions/194846/is-there-any-kind-of-hash-code-function-in-javascript
 */
expect val Any.doorIdentityHashCode: Int

