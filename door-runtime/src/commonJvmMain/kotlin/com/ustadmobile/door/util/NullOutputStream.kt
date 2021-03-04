package com.ustadmobile.door.util

import java.io.OutputStream

/**
 * Null output stream that sends bytes nowhere
 */
class NullOutputStream: OutputStream() {

    override fun write(p0: ByteArray) {

    }

    override fun write(p0: ByteArray, p1: Int, p2: Int) {

    }

    override fun write(p0: Int) {

    }
}