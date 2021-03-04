package com.ustadmobile.door.ext

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.security.MessageDigest

class StringByteArrayExtTest  {

    @Test
    fun givenByteArray_whenConvertedToStringAndBackToByteArray_thenShouldBeTheSame() {
        val testFileBytes = this::class.java
                .getResourceAsStream("/test-resources/cat-pic0.jpg" )
                .readBytes()
        val messageDigest = MessageDigest.getInstance("MD5")
        messageDigest.update(testFileBytes)
        val md5Bytes = messageDigest.digest()

        val md5AsStr = md5Bytes.toHexString()

        assertArrayEquals("d", md5Bytes, md5AsStr.hexStringToByteArray())
    }

}