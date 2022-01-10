package com.ustadmobile.door.jdbc.ext

import android.os.Looper
import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.ResultSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun PreparedStatement.executeQueryAsyncKmp(): ResultSet {
    return if(Looper.myLooper() == Looper.getMainLooper()) {
        withContext(Dispatchers.IO) { executeQuery() }
    }else {
        executeQuery()
    }
}

actual suspend fun PreparedStatement.executeUpdateAsyncKmp(): Int {
    return if(Looper.myLooper() == Looper.getMainLooper()) {
        withContext(Dispatchers.IO) { executeUpdate() }
    }else {
        executeUpdate()
    }
}