package com.ustadmobile.door.attachments

import com.ustadmobile.door.sqljsjdbc.IndexedDb
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestDoorAttachmentsExt {

    @Test
    fun givenEntityWithAttachment_whenStored_thenShouldStoreHopefully()  = GlobalScope.promise {
        val blob = Blob(arrayOf("Hello World"), BlobPropertyBag(type = "text/plain"))
        IndexedDb.storeBlob("test_db", IndexedDb.ATTACHMENT_STORE_NAME, "tablename/foo", blob)
        val retrieved = IndexedDb.retrieveBlob("test_db", IndexedDb.ATTACHMENT_STORE_NAME,
            "tablename/foo")
        assertNotNull(retrieved)
        val promise: Promise<String> = retrieved.asDynamic().text()
        val text = promise.await()
        assertEquals("Hello World", text, "Got same value back")

    }

}