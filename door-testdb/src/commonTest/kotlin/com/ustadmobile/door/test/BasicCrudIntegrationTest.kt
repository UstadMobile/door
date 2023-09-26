package com.ustadmobile.door.test

import app.cash.paging.*
import com.ustadmobile.door.util.systemTimeInMillis
import db2.ExampleDatabase2
import db2.ExampleEntity2
import db2.ExampleLinkEntity
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlin.random.Random
import kotlin.test.*

/**
 * These tests run basic insert, update, query functions to ensure that generated implementations do what they are
 * supposed to do. This includes testing Flow and PagingSource return types.
 */
class BasicCrudIntegrationTest : AbstractCommonTest() {
    data class ExampleDbContext(
        val testScope: TestScope,
        val exampleDb2: ExampleDatabase2,
    )

    val startTime = systemTimeInMillis()

    @BeforeTest
    fun setup() {
        initNapierLog()
        Napier.d("Start time = $startTime\n")
    }

    @Test
    fun givenEntryInserted_whenQueried_shouldBeEqual() = runExampleDbTest {
        val entityToInsert = ExampleEntity2(0, "Bob", 50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(entityToInsert)

        val entityFromQuery = exampleDb2.exampleDao2().findByUidAsync(entityToInsert.uid)

        assertNotEquals(0, entityToInsert.uid)
        assertEquals(entityToInsert, entityFromQuery,
            message = "Entity retrieved from database is the same as entity inserted")
    }


    @Test
    fun givenEntryInserted_whenSingleValueQueried_shouldBeEqual() = runExampleDbTest {
        val entityToInsert = ExampleEntity2(0, "Bob" + systemTimeInMillis(),
                50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(entityToInsert)


        assertEquals(entityToInsert.name,
            exampleDb2.exampleDao2().findNameByUidAsync(entityToInsert.uid),
            message = "Select single column method returns expected value",)
    }

    @Test
    fun givenEntitiesInserted_whenQueryWithEmbeddedValueRuns_shouldReturnBoth() = runExampleDbTest {
        val entityToInsert = ExampleEntity2(0, "Linked", 50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(entityToInsert)

        val linkedEntity = ExampleLinkEntity(Random.nextLong(0, 1000), entityToInsert.uid)
        exampleDb2.exampleLinkedEntityDao().insertAsync(linkedEntity)

        val entityWithEmbedded = exampleDb2.exampleDao2().findByUidWithLinkEntityAsync(entityToInsert.uid)
        assertEquals(linkedEntity.eeUid, entityWithEmbedded!!.link!!.eeUid,
            message = "Embedded entity is loaded into query result",)
    }

    @Test
    fun givenEntityInsertedWithNoCorrespondingEmbeddedEntity_whenQueryWithEmbeddedValueRuns_embeddedObjectShouldBeNull() = runExampleDbTest {
        val entityToInsert = ExampleEntity2(0, "Not Linked", 50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(entityToInsert)

        val entityWithEmbedded = exampleDb2.exampleDao2().findByUidWithLinkEntityAsync(entityToInsert.uid)

        assertNull(entityWithEmbedded!!.link,
            message = "Embedded entity is null where there is no matching entity on the right hand side of join")
    }


    @Test
    fun givenEntitiesInserted_whenFindAllCalled_shouldReturnBoth() = runExampleDbTest {
        val entities = listOf(ExampleEntity2(name = "e1", someNumber = 42),
                ExampleEntity2(name = "e2", someNumber = 43))
        exampleDb2.exampleDao2().insertListAsync(entities)

        val entitiesFromQuery = exampleDb2.exampleDao2().findAllAsync()
        assertEquals(2, entitiesFromQuery.size,
            message = "Found correct number of entities inserted")
    }

    @Test
    fun givenEntityInserted_whenUpdateSingleItemNoReturnTypeCalled_thenValueShouldBeUpdated() = runExampleDbTest {
        val entityToInsert = ExampleEntity2(name = "UpdateMe", someNumber =  50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(entityToInsert)

        val nameBeforeInsert = exampleDb2.exampleDao2().findNameByUidAsync(entityToInsert.uid)

        entityToInsert.name = "Update${systemTimeInMillis()}"
        exampleDb2.exampleDao2().updateSingleItemAsync(entityToInsert)

        assertEquals( "UpdateMe", nameBeforeInsert,
            message = "Name before insert is first given name")
        assertEquals(entityToInsert.name, exampleDb2.exampleDao2().findNameByUidAsync(entityToInsert.uid),
            message = "Name after insert is updated name")
    }

    @Test
    fun givenEntityInserted_whenUpdatedSingleItemReturnCountCalled_thenShouldUpdateAndReturn1() = runExampleDbTest {
        val entityToInsert = ExampleEntity2(name = "UpdateMe", someNumber =  50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(entityToInsert)

        entityToInsert.name = "Update${systemTimeInMillis()}"

        val updateCount = exampleDb2.exampleDao2().updateSingleItemAndReturnCountAsync(entityToInsert)

        assertEquals(1, updateCount, message = "Update count = 1")
        assertEquals(entityToInsert.name, exampleDb2.exampleDao2().findNameByUidAsync(entityToInsert.uid),
            message = "Name after insert is updated name")
    }

    @Test
    fun givenEntitiesInserted_whenUpdateListNoReturnTypeCalled_thenBothItemsShouldBeUpdated() = runExampleDbTest {
        val entities = listOf(ExampleEntity2(name = "e1", someNumber = 42),
                ExampleEntity2(name = "e2", someNumber = 43))
        entities.forEach { it.uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(it) }

        entities.forEach { it.name += systemTimeInMillis() }
        exampleDb2.exampleDao2().updateListAsync(entities)

        entities.forEach {
            assertEquals(it.name, exampleDb2.exampleDao2().findNameByUidAsync(it.uid),
                message = "Entity was updated")
        }
    }


    @Test
    fun givenEntryInsertedAsync_whenQueriedAsync_shouldBeEqual() = runExampleDbTest {
        val entityToInsert = ExampleEntity2(0, "Bob", 50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(entityToInsert)

        val entityFromQuery = exampleDb2.exampleDao2().findByUidAsync(entityToInsert.uid)

        assertNotEquals(0, entityToInsert.uid)
        assertEquals(entityToInsert, entityFromQuery,
            message = "Entity retrieved from database is the same as entity inserted")
    }

    @Test
    fun givenOneEntry_whenQueriedAsFlowAndNewEntityInserted_shouldEmitFirstValueThenSecond() = runExampleDbTest {
        val coroutineScope = CoroutineScope(Dispatchers.Default + Job())

        ExampleEntity2().apply {
            name = "Bob"
            uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(this)
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
            uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(this)
        }

        val secondEmission = withTimeout(1000) {
            flow.first()
        }

        assertEquals( 1, firstEmission.size,
            "Initial result had one entity in list")
        assertEquals( 2, secondEmission.size,
                message = "Second result had two entities in list")
        flowJob.cancelAndJoin()

        coroutineScope.cancel()
    }

    @Test
    fun givenQueryPrimitiveReturnTypeNotNullableAsync_whenNoRowsReturned_thenShouldReturnDefaultValue() = runExampleDbTest {
        assertEquals(0, exampleDb2.exampleDao2().findSingleNotNullablePrimitiveAsync(Int.MAX_VALUE),
            message = "Primitive not nullable type query will return default value when no rows match")
    }

    @Test
    fun givenQueryPrimitiveReturnTypeNullableAsync_whenNoRowsReturned_thenShouldReturnNull() = runExampleDbTest {
        assertNull(exampleDb2.exampleDao2().findSingleNullablePrimitiveAsync(Int.MAX_VALUE),
            "Primitive nullable type query will return null when no rows match")
    }


    @Test
    fun givenQueryWithNullPrimitiveParam_whenQueried_thenShouldGetResult() = runExampleDbTest {
        exampleDb2.exampleDao2().insertAsyncAndGiveId(ExampleEntity2().apply {
            name = "Lenny"
            rewardsCardNumber = null
        })

        assertEquals(exampleDb2.exampleDao2().findWithNullableIntAsync(null)?.name,
            "Lenny")
    }

    @Test
    fun givenPagingSourceParameter_whenQueried_pagingSourceShouldReturnPagedRows() = runExampleDbTest {
        exampleDb2.exampleDao2().insertListAsync(
            (0 until 500).map {number ->
                ExampleEntity2().apply {
                    name = "Customer_$number"
                    rewardsCardNumber = number
                }
            }
        )

        val minRewardNum = 20

        val allResultsInList = exampleDb2.exampleDao2().findAllWithRewardNumberAsListAsync(minRewardNum)

        val loadSize = 50
        val pagingSource: PagingSource<Int, ExampleEntity2> = exampleDb2.exampleDao2()
            .findAllWithRewardNumberAsPagingSource(minRewardNum)

        val allPages = mutableListOf<PagingSourceLoadResultPage<Int, ExampleEntity2>>()

        var loadResultPage: PagingSourceLoadResultPage<Int, ExampleEntity2>? = null
        while(loadResultPage == null || loadResultPage.nextKey != null) {
            val loadParams: PagingSourceLoadParams<Int> = loadResultPage?.let { PagingSourceLoadParamsAppend(
                it.nextKey ?: 0, loadSize, true)
            } ?: PagingSourceLoadParamsRefresh(null, loadSize, true)
            loadResultPage = pagingSource.load(loadParams) as PagingSourceLoadResultPage<Int, ExampleEntity2>
            allPages += loadResultPage
        }

        val page1 = allPages.first()
        val page2 = allPages[1]

        assertEquals(loadSize, page1.data.size,
            message = "Loaded correct number of items")
        assertNull(page1.prevKey,
            message = "Previous key for first row is null")
        assertEquals(minRewardNum, page1.data.first().rewardsCardNumber,
            message = "Initial reward num should match minRewardNumber")

        assertEquals(minRewardNum + loadSize, page2.data.first().rewardsCardNumber,
            "Initial reward num in second list should be minRewardNumber + loadSize")

        assertEquals(allResultsInList, allPages.flatMap { it.data },
            "When combining all pages, this should be the same as getting it all in one list")
    }

    @Test
    fun givenPagingSource_whenQueriedAndDataChanged_thenShouldInvalidated() = runExampleDbTest {
        exampleDb2.exampleDao2().insertListAsync(
            (0 until 20).map {number ->
                ExampleEntity2().apply {
                    name = "Customer_$number"
                    rewardsCardNumber = number
                }
            }
        )

        val pagingSource = exampleDb2.exampleDao2().findAllWithRewardNumberAsPagingSource(0)
        pagingSource.load(
            PagingSourceLoadParamsRefresh(
                key = null ,
                loadSize = 50,
                placeholdersEnabled = false,
            ) as PagingSourceLoadParams<Int>
        )

        val invalidationCompletable = CompletableDeferred<Boolean>()
        pagingSource.registerInvalidatedCallback {
            invalidationCompletable.complete(true)
        }

        exampleDb2.exampleDao2().insertAsync(
            ExampleEntity2(
                name = "New customer",
                rewardsCardNumber = 1042,
            )
        )

        withTimeout(1000) {
            assertTrue(invalidationCompletable.await())
        }
    }

    companion object {
        fun runExampleDbTest(
            block: suspend ExampleDbContext.() -> Unit
        ): TestResult {
            return runTestWithRealClock {
                val db = makeExample2Database(nodeId = 1L)
                try {
                    block(ExampleDbContext(this, db))
                }finally {
                    db.close()
                }
            }
        }
    }

}