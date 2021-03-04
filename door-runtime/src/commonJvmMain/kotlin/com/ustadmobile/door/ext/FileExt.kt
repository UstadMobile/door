package com.ustadmobile.door.ext

import com.ustadmobile.door.util.NullOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import java.util.zip.GZIPInputStream

fun File.copyAndGetMd5(dest: OutputStream, inflate: Boolean = false): ByteArray {
    val messageDigest = MessageDigest.getInstance("MD5")
    val inStream = if(inflate) {
        GZIPInputStream(FileInputStream(this))
    }else {
        FileInputStream(this)
    }

    val digestInputStream = DigestInputStream(inStream, messageDigest)

    digestInputStream.use {
        it.copyTo(dest)
        it.close()
    }

    dest.flush()

    return messageDigest.digest()
}

val File.md5Sum: ByteArray
    get() = copyAndGetMd5(NullOutputStream())

val File.inflatedMd5Sum: ByteArray
    get() = copyAndGetMd5(NullOutputStream(), inflate = true)

fun File.copyAndGetMd5(dest: File) = FileOutputStream(dest).use {
    copyAndGetMd5(it)
}

fun File.gzipAndGetMd5(dest: File) = GZIPOutputStream(FileOutputStream(dest)).use {
    copyAndGetMd5(it)
}
