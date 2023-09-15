package com.ustadmobile.door.nodeevent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ustadmobile.door.DatabaseBuilder
import db3.ExampleDb3

class NodeEventTest : AbstractNodeEventTest() {

    override fun makeExampleDb(nodeId: Long): ExampleDb3 {
        val context = ApplicationProvider.getApplicationContext<Context>()

        return DatabaseBuilder.databaseBuilder(context, ExampleDb3::class, "ExampleDb3_$nodeId:", nodeId)
            .build().also {
                it.clearAllTables()
            }
    }




}