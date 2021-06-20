package com.ustadmobile.door.ext

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gets the file name for a Uri (e.g. file) that has just been selected. When a file is selected
 * using ActivityResultContracts.GetContent the Uri that is returned might or might not have the
 * actual filename in the path. Therefor we run a query to try and get the specified DISPLAY_NAME,
 * and only if that doesn't return a result, then we fallback to using substringAfterLast("/").
 *
 * See:
 * https://developer.android.com/training/secure-file-sharing/retrieve-info
 *
 * @param uri the Uri of a file that has just been selected
 */
suspend fun ContentResolver.getFileName(uri: Uri): String {
    var fileName: String? = null

    withContext(Dispatchers.IO) {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null).use {
            if(it != null && it.moveToFirst()) {
                fileName = it.getString(0)
            }
        }
    }

    return fileName ?: uri.path?.substringAfterLast("/") ?: uri.toString()
}
