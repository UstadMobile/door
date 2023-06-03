package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabaseCallbackStatementList
import com.ustadmobile.door.DoorSqlDatabase
import com.ustadmobile.door.paging.LoadParams
import com.ustadmobile.door.paging.LoadResult
import com.ustadmobile.door.paging.PagingSource
import db2.ExampleDatabase2
import db2.ExampleEntity2
import db2.ExampleLinkEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert
import org.junit.Before

import org.junit.Test

/**
 * These tests run basic insert, update, query functions to ensure that generated implementations do what they are
 * supposed to do.
 */
class TestDbBuilderKt {

    lateinit var exampleDb2: ExampleDatabase2

    @Before
    fun openAndClearDb() {
        exampleDb2 = DatabaseBuilder.databaseBuilder( ExampleDatabase2::class, "jdbc:sqlite::memory:",1L)
            .addCallback(object: DoorDatabaseCallbackStatementList {
                override fun onOpen(db: DoorSqlDatabase): List<String> {
                    return listOf()
                }

                override fun onCreate(db: DoorSqlDatabase): List<String> {
                    return listOf("UPDATE ExampleEntity2 SET someNumber = someNumber + 1")
                }
            })
            .build()
        exampleDb2.clearAllTables()
    }

    @Test
    fun givenEntryInserted_whenQueried_shouldBeEqual() {
        val entityToInsert = ExampleEntity2(0, "Bob", 50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAndReturnId(entityToInsert)

        val entityFromQuery = exampleDb2.exampleDao2().findByUid(entityToInsert.uid)

        Assert.assertNotEquals(0, entityToInsert.uid)
        Assert.assertEquals("Entity retrieved from database is the same as entity inserted",
                entityToInsert, entityFromQuery)
    }


    @Test
    fun givenEntryInserted_whenSingleValueQueried_shouldBeEqual() {
        val entityToInsert = ExampleEntity2(0, "Bob" + System.currentTimeMillis(),
                50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAndReturnId(entityToInsert)


        Assert.assertEquals("Select single column method returns expected value",
                entityToInsert.name, exampleDb2.exampleDao2().findNameByUid(entityToInsert.uid))
    }

    @Test
    fun givenEntitiesInserted_whenQueryWithEmbeddedValueRuns_shouldReturnBoth() {
        val entityToInsert = ExampleEntity2(0, "Linked", 50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAndReturnId(entityToInsert)

        val linkedEntity = ExampleLinkEntity((Math.random() * 1000).toLong(), entityToInsert.uid)
        exampleDb2.exampleLinkedEntityDao().insert(linkedEntity)

        val entityWithEmbedded = exampleDb2.exampleDao2().findByUidWithLinkEntity(entityToInsert.uid)
        Assert.assertEquals("Embedded entity is loaded into query result",
                linkedEntity.eeUid, entityWithEmbedded!!.link!!.eeUid)
    }

    @Test
    fun givenEntityInsertedWithNoCorrespondingEmbeddedEntity_whenQueryWithEmbeddedValueRuns_embeddedObjectShouldBeNull() {
        val entityToInsert = ExampleEntity2(0, "Not Linked", 50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAndReturnId(entityToInsert)

        val entityWithEmbedded = exampleDb2.exampleDao2().findByUidWithLinkEntity(entityToInsert.uid)

        Assert.assertNull("Embedded entity is null where there is no matching entity on the right hand side of join",
                entityWithEmbedded!!.link)
    }


    @Test
    fun givenEntitiesInserted_whenFindAllCalled_shouldReturnBoth(){
        val entities = listOf(ExampleEntity2(name = "e1", someNumber = 42),
                ExampleEntity2(name = "e2", someNumber = 43))
        exampleDb2.exampleDao2().insertList(entities)

        val entitiesFromQuery = exampleDb2.exampleDao2().findAll()
        Assert.assertEquals("Found correct number of entities inserted",2,
                entitiesFromQuery.size)
    }

    @Test
    fun givenEntityInserted_whenUpdateSingleItemNoReturnTypeCalled_thenValueShouldBeUpdated() {
        val entityToInsert = ExampleEntity2(name = "UpdateMe", someNumber =  50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAndReturnId(entityToInsert)

        val nameBeforeInsert = exampleDb2.exampleDao2().findNameByUid(entityToInsert.uid)

        entityToInsert.name = "Update${System.currentTimeMillis()}"
        exampleDb2.exampleDao2().updateSingleItem(entityToInsert)

        Assert.assertEquals("Name before insert is first given name", "UpdateMe",
                nameBeforeInsert)
        Assert.assertEquals("Name after insert is updated name", entityToInsert.name,
                exampleDb2.exampleDao2().findNameByUid(entityToInsert.uid))
    }

    @Test
    fun givenEntityInserted_whenUpdatedSingleItemReturnCountCalled_thenShouldUpdateAndReturn1() {
        val entityToInsert = ExampleEntity2(name = "UpdateMe", someNumber =  50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAndReturnId(entityToInsert)

        entityToInsert.name = "Update${System.currentTimeMillis()}"

        val updateCount = exampleDb2.exampleDao2().updateSingleItemAndReturnCount(entityToInsert)

        Assert.assertEquals("Update count = 1", 1, updateCount)
        Assert.assertEquals("Name after insert is updated name", entityToInsert.name,
                exampleDb2.exampleDao2().findNameByUid(entityToInsert.uid))
    }

    @Test
    fun givenEntitiesInserted_whenUpdateListNoReturnTypeCalled_thenBothItemsShouldBeUpdated() {
        val entities = listOf(ExampleEntity2(name = "e1", someNumber = 42),
                ExampleEntity2(name = "e2", someNumber = 43))
        entities.forEach { it.uid = exampleDb2.exampleDao2().insertAndReturnId(it) }

        entities.forEach { it.name += System.currentTimeMillis() }
        exampleDb2.exampleDao2().updateList(entities)

        entities.forEach { Assert.assertEquals("Entity was updated", it.name,
                exampleDb2.exampleDao2().findNameByUid(it.uid)) }
    }


    @Test
    fun givenEntryInsertedAsync_whenQueriedAsync_shouldBeEqual() {
        runBlocking {
            val entityToInsert = ExampleEntity2(0, "Bob", 50)
            entityToInsert.uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(entityToInsert)

            val entityFromQuery = exampleDb2.exampleDao2().findByUidAsync(entityToInsert.uid)

            Assert.assertNotEquals(0, entityToInsert.uid)
            Assert.assertEquals("Entity retrieved from database is the same as entity inserted",
                entityToInsert, entityFromQuery)
        }

    }

    @Test
    fun givenOneEntry_whenQueriedAsFlowAndNewEntityInserted_shouldEmitFirstValueThenSecond() {
        val coroutineScope = CoroutineScope(Dispatchers.Default + Job())
        runBlocking {
            ExampleEntity2().apply {
                name = "Bob"
                uid = exampleDb2.exampleDao2().insertAndReturnId(this)
            }

            val flowJob = coroutineScope.async {
                exampleDb2.exampleDao2().queryAllAsFlow()
            }

            val flow = flowJob.await()

            val firstEmission = withTimeout(1000) {
                flow.first()
            }

            ExampleEntity2().apply {
                name = "Lenny"
                uid = exampleDb2.exampleDao2().insertAndReturnId(this)
            }

            val secondEmission = withTimeout(1000) {
                flow.first()
            }

            Assert.assertEquals("Initial result had one entity in list", firstEmission.size, 1)
            Assert.assertEquals("Second result had two entities in list", secondEmission.size, 2)
            flowJob.cancelAndJoin()
        }
        coroutineScope.cancel()
    }

    @Test(expected = NullPointerException::class)
    fun givenQueryEntityReturnTypeNotNullable_whenNoRowsReturned_thenShouldThrowNullPointerException() {
        exampleDb2.exampleDao2().findSingleNotNullableEntity(Int.MAX_VALUE)
    }

    @Test(expected = NullPointerException::class)
    fun givenQueryEntityReturnTypeNotNullableAsync_whenNoRowsReturned_thenShouldThrowNullPointerExecption() = runBlocking {
        exampleDb2.exampleDao2().findSingleNotNullableEntityAsync(Int.MAX_VALUE)
        Unit
    }

    @Test
    fun givenQueryPrimitiveReturnTypeNotNullable_whenNoRowsReturned_thenShouldReturnDefaultValue() {
        Assert.assertEquals("Primitive not nullable type query will return default value when no rows match",
            0, exampleDb2.exampleDao2().findSingleNotNullablePrimitive(Int.MAX_VALUE))
    }

    @Test
    fun givenQueryPrimitiveReturnTypeNotNullableAsync_whenNoRowsReturned_thenShouldReturnDefaultValue() = runBlocking {
        Assert.assertEquals("Primitive not nullable type query will return default value when no rows match",
            0, exampleDb2.exampleDao2().findSingleNotNullablePrimitiveAsync(Int.MAX_VALUE))
    }

    @Test
    fun givenQueryPrimitiveReturnTypeNullable_whenNoRowsReturn_thenShouldReturnNull(){
        Assert.assertNull("Primitive nullable type query will return null when no rows match",
            exampleDb2.exampleDao2().findSingleNullablePrimitive(Int.MAX_VALUE))
    }

    @Test
    fun givenQueryPrimitiveReturnTypeNullableAsync_whenNoRowsReturned_thenShouldReturnNull() = runBlocking {
        Assert.assertNull("Primitive nullable type query will return null when no rows match",
            exampleDb2.exampleDao2().findSingleNullablePrimitiveAsync(Int.MAX_VALUE))
    }


    @Test
    fun givenQueryWithNullPrimitiveParam_whenQueried_thenShouldGetResult() {
        exampleDb2.exampleDao2().insertAndReturnId(ExampleEntity2().apply {
            name = "Lenny"
            rewardsCardNumber = null
        })

        Assert.assertEquals(exampleDb2.exampleDao2().findWithNullableInt(null)?.name,
            "Lenny")
    }

    @Test(timeout = 5000)
    fun givenPagingSourceParameter_whenQueried_pagingSourceShouldReturnPagedRows() {
        exampleDb2.exampleDao2().insertList(
            (0 until 500).map {number ->
                ExampleEntity2().apply {
                    name = "Customer_$number"
                    rewardsCardNumber = number
                }
            }
        )

        val minRewardNum = 20

        val allResultsInList = exampleDb2.exampleDao2().findAllWithRewardNumberAsList(minRewardNum)

        val loadSize = 50
        val pagingSource: PagingSource<Int, ExampleEntity2> = exampleDb2.exampleDao2()
            .findAllWithRewardNumberAsPagingSource(minRewardNum)

        val allPages = mutableListOf<LoadResult.Page<Int, ExampleEntity2>>()
        runBlocking {
            var loadResultPage: LoadResult.Page<Int, ExampleEntity2>? = null
            while(loadResultPage == null || loadResultPage.nextKey != null) {
                val loadParams: LoadParams<Int> = loadResultPage?.let { LoadParams.Append(
                    it.nextKey ?: 0, loadSize, true)
                } ?: LoadParams.Refresh(null, loadSize, true)
                loadResultPage = pagingSource.load(loadParams) as LoadResult.Page<Int, ExampleEntity2>
                allPages += loadResultPage
            }
        }

        val page1 = allPages.first()
        val page2 = allPages[1]

        Assert.assertEquals("Loaded correct number of items", loadSize, page1.data.size)
        Assert.assertNull("Previous key for first row is null", page1.prevKey)
        Assert.assertEquals("Initial reward num should match minRewardNumber",
            minRewardNum, page1.data.first().rewardsCardNumber)

        Assert.assertEquals("Initial reward num in second list should be minRewardNumber + loadSize",
            minRewardNum + loadSize, page2.data.first().rewardsCardNumber)

        Assert.assertEquals("When combining all pages, this should be the same as getting it all in one list",
            allResultsInList, allPages.flatMap { it.data })
    }

}