package com.ustadmobile.door.ext

import com.ustadmobile.door.attachments.SparkMD5
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.FileReader
import kotlin.test.Test
import kotlin.test.assertNotNull

class BlobExtTest {

    @Test
    fun givenBlob_whenGetMd5Called_thenShouldReturnMd5() = GlobalScope.promise {
        console.log(SparkMD5.hash("Hello"))
        val blob = Blob(arrayOf("Hello World"), BlobPropertyBag(type = "text/plain"))
        console.log("Blob md5 = " + blob.md5())
    }

}