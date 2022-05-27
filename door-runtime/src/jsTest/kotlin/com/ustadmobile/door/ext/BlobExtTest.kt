package com.ustadmobile.door.ext

import com.ustadmobile.door.attachments.SparkMD5
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.test.Test
import kotlin.test.assertEquals

class BlobExtTest {

    @Test
    fun givenBlob_whenGetMd5Called_thenShouldReturnMd5() = GlobalScope.promise {
        val blob = Blob(arrayOf("Hello World"), BlobPropertyBag(type = "text/plain"))
        val blobMd5 = blob.md5()
        console.log("Blob md5 = blobmd5\n")
        val directHash = SparkMD5.hash("Hello World")
        console.log("Direct hash = $directHash\n")
        assertEquals(directHash, blobMd5, "Hash of blob matches expected hash")
    }

}