package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.DataSource
import java.io.Closeable

actual fun DataSource.closeIfCloseable() {
    if(this is Closeable)
        close()

}
