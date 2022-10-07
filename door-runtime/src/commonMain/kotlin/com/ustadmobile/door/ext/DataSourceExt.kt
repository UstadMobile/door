package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.DataSource

expect fun DataSource.closeIfCloseable()
