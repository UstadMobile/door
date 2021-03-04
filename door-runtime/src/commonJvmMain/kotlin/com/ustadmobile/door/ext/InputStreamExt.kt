package com.ustadmobile.door.ext
import java.io.FileOutputStream
import java.io.File
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/**
 * Write the given InputStream to a file, flush, and close
 */
fun InputStream.writeToFile(file: File) {
    use { inStream ->
        FileOutputStream(file).use { outStream ->
            inStream.copyTo(outStream)
            outStream.flush()
        }
    }
}

/**
 * Write the given InputStream to a file, flush, and return the MD5 of the data that was written.
 * This does NOT close the InputStram itself.
 *
 * @param destFile the file ot which the inputstream should be written
 * @param gzip if true, then gzip the output. The MD5Sum reflects the MD5Sum of the original
 * data as it is read from the stream, Gzipping it when writing to disk will not change it.
 */
fun InputStream.writeToFileAndGetMd5(destFile: File, gzip: Boolean = false) : ByteArray {
    val messageDigest = MessageDigest.getInstance("MD5")
    val inStream = DigestInputStream(this, messageDigest)

    val outStream = if(gzip) {
        GZIPOutputStream(FileOutputStream(destFile))
    }else {
        FileOutputStream(destFile)
    }

    outStream.use { outStream ->
        inStream.copyTo(outStream)
        outStream.flush()
    }

    return messageDigest.digest()
}
