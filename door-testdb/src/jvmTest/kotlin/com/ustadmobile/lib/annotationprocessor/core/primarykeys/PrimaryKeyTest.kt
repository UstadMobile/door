package com.ustadmobile.lib.annotationprocessor.core.primarykeys

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorPrimaryKeyManager
import com.ustadmobile.door.ext.doorPrimaryKeyManager
import com.ustadmobile.door.ext.withDoorTransaction
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import repdb.RepDb
import repdb.RepEntity

/**
 * Tests to ensure primary keys on replicate entities behave as expected
 */
class PrimaryKeyTest {

    private lateinit var db: RepDb

    @Before
    fun setup() {
        db = DatabaseBuilder.databaseBuilder(Any(), RepDb::class,"RepDb")
            .build().also {
                it.clearAllTables()
            }
    }

    @Test
    fun givenEntityWithUnsetPrimaryKey_whenInserted_thenShouldReturnAutoGenPk() {
        val insertedPk = db.repDao.insert(RepEntity().apply {
            this.reNumField = 42
        })

        Assert.assertNotEquals("insertedPK != 0", 0L, insertedPk)
        Assert.assertTrue("Inserted PK was set by primarykeymanager, not something from autoincremenet",
            insertedPk > 100000)
        Assert.assertNotNull("Lookup by inserted primary key works",
            db.repDao.findByUid(insertedPk))
    }

    @Test
    fun givenTransactionDb_whenInserted_thenShouldUseRootPrimaryKeyManager() {
        val dbPkManager = db.doorPrimaryKeyManager
        lateinit var transactionPkManager: DoorPrimaryKeyManager

        db.withDoorTransaction(RepDb::class) { transactionDb ->
            transactionPkManager = transactionDb.doorPrimaryKeyManager
            transactionDb.repDao.insert(RepEntity().apply {
                this.reNumField = 100
            })
        }

        Assert.assertTrue("Transaction and db itself use exactly the same instance for primary key manager",
            dbPkManager === transactionPkManager)
    }

}