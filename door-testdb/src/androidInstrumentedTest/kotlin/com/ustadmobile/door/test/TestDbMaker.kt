package com.ustadmobile.door.test

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ustadmobile.door.DatabaseBuilder
import db2.ExampleDatabase2
import db3.ExampleDb3


actual suspend fun makeExample2Database(nodeId: Long): ExampleDatabase2 {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return DatabaseBuilder.databaseBuilder(context, ExampleDatabase2::class,
        "ExampleDb_${nodeId}", nodeId).build().also {
            it.clearAllTables()
    }
}


actual suspend fun makeExample3Database(nodeId: Long): ExampleDb3 {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return DatabaseBuilder.databaseBuilder(context, ExampleDb3::class,
        "ExampleDb3_${nodeId}", nodeId).build().also {
            it.clearAllTables()
    }
}

