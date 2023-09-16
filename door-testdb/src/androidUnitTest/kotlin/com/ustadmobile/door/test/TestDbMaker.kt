package com.ustadmobile.door.test

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ustadmobile.door.DatabaseBuilder
import db2.ExampleDatabase2


actual fun makeInMemoryTestDb(nodeId: Long) {

}

actual suspend fun makeExample2Database(nodeId: Long): ExampleDatabase2 {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return DatabaseBuilder.databaseBuilder(context, ExampleDatabase2::class,
        "ExampleDb_${nodeId}", nodeId).build().also {
            it.clearAllTables()
    }
}

