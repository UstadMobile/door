package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.door.*
import db2.ExampleDatabase2
import db2.ExampleEntity2
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


/*
 * Note: this can be adapted for multiplatform async testing using the following techniques:
 *   https://blog.kotlin-academy.com/testing-common-modules-66b39d641617
 */
class TestDbBuilderKtKt {

    lateinit var exampleDb2: ExampleDatabase2

    @Before
    fun setup() {
        exampleDb2 = DatabaseBuilder.databaseBuilder(Any(), ExampleDatabase2::class, "db1").build()
        exampleDb2.clearAllTables()
    }

    @Test
    fun givenDataInserted_whenUpdateMade_thenLiveDataShouldBeUpdated() {
        runBlocking {
            val liveData = exampleDb2.exampleDao2().findByMinUidLive()
            val channel = Channel<List<ExampleEntity2>?>(1)
            val observerFn = object : DoorObserver<List<ExampleEntity2>?> {
                override fun onChanged(t: List<ExampleEntity2>?) {
                    if(t?.size == 1) {
                        channel.offer(t)
                    }
                }
            }

            liveData.observeForever(observerFn)

            delay(1000)
            val entity = ExampleEntity2(name = "Bob", someNumber = 50)
            entity.uid = exampleDb2.exampleDao2().insertAndReturnId(entity)

            val list = withTimeout(10000) { channel.receive() }
            assertNotNull(list, message = "List is not null")
            assertEquals(1, list.size, "List size is 1")

            liveData.removeObserver(observerFn)
        }
    }

    @Test
    fun givenDataInserted_whenDeleted_shouldNotBePresentedAnymore() {
        val entity = ExampleEntity2(name = "bob", someNumber = 100)
        entity.uid = exampleDb2.exampleDao2().insertAndReturnId(entity)
        val queryResultBeforeDelete = exampleDb2.exampleDao2().findByUid(entity.uid)

        exampleDb2.exampleDao2().deleteSingle(entity)

        assertNotNull(queryResultBeforeDelete, "Entity was in database before delete")
        assertNull(exampleDb2.exampleDao2().findByUid(entity.uid),
                "Entity not in database anymore after delete")
    }

    @Test
    fun givenMultipleEntitiesInserted_whenDeleteCalled_dbShouldBeEmpty() {
        val entityList = listOf(ExampleEntity2(name = "e1", someNumber = 42),
                ExampleEntity2(name = "e2", someNumber = 43))
        entityList.forEach { it.uid = exampleDb2.exampleDao2().insertAndReturnId(it) }
        val numEntitiesBefore = exampleDb2.exampleDao2().countNumEntities()

        exampleDb2.exampleDao2().deleteList(entityList)

        assertEquals(entityList.size, numEntitiesBefore,
                "Before delete, there were two entities in DB")

        assertEquals(0, exampleDb2.exampleDao2().countNumEntities(),
                "After delete, there are zero entities in DB")
    }

    @Test
    fun givenEntitiesInserted_whenArrayQueryParameterRuns_shouldFindInsertedEntities() {
        val entityList = listOf(ExampleEntity2(name = "e1", someNumber = 42),
                ExampleEntity2(name = "e2", someNumber = 43))
        entityList.forEach { it.uid = exampleDb2.exampleDao2().insertAndReturnId(it) }

        val queryResults = exampleDb2.exampleDao2().queryUsingArray(entityList.map { it.uid})

        assertEquals(entityList.size, queryResults.size)
    }

    @Test
    fun givenEntitiesInserted_whenArrayQueryParameterRunsWithOtherVals_shouldNotFindEntities() {
        val entityList = listOf(ExampleEntity2(name = "e1", someNumber = 42),
                ExampleEntity2(name = "e2", someNumber = 43))
        entityList.forEach { it.uid = exampleDb2.exampleDao2().insertAndReturnId(it) }

        val queryResults = exampleDb2.exampleDao2().queryUsingArray(listOf(-1, -2))
        assertEquals(0, queryResults.size)
    }

    @Test
    fun givenEntityWithInheritedInterfaceMethod_whenMethodCalled_shouldInsert() {
        val entity = ExampleEntity2(name = "WithInterface", someNumber =  43)
        entity.uid = exampleDb2.examlpeDaoWithInterface().insertOne(entity)

        assertEquals(entity, exampleDb2.exampleDao2().findByUid(entity.uid),
                "Entity inserted using DAO implementing interface is found")
    }

    @Test
    fun givenRawQueryWithSingularResult_whneQueryRuns_entityResultShouldMatchEntityInserte() {
        val entity = ExampleEntity2(name = "WithInterface", someNumber =  43)
        entity.uid = exampleDb2.examlpeDaoWithInterface().insertOne(entity)

        val entityFromQuery = exampleDb2.exampleDao2().rawQueryForSingleValue(
                SimpleDoorQuery("SELECT * FROM ExampleEntity2 WHERE uid = ?", arrayOf<Any>(entity.uid)))
        assertEquals(entity, entityFromQuery, "Entity inserted using DAO is equal to result with raw query")
    }

    @Test
    fun givenRawQueryWithArrayParam_whenQueryRuns_shouldReturnMatchingRows() {
        val entityList = (1..3).map { ExampleEntity2(name = "Name$it", someNumber = it.toLong())}
        entityList.forEach { it.uid = exampleDb2.exampleDao2().insertAndReturnId(it) }
        val queryResult = exampleDb2.exampleDao2().rawQueryWithArrParam(
                SimpleDoorQuery("SELECT * FROM ExampleEntity2 WHERE uid IN (?)",
                        arrayOf(arrayOf(entityList[1].uid, entityList[2].uid))))
        assertEquals(2, queryResult.size, "Raw query with array param returns expected number of results")
    }

}