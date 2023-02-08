package db2

import com.ustadmobile.door.paging.*

class DummyPagingSource(): PagingSource<Int, ExampleEntity2>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExampleEntity2> {
        return DoorLoadResult.Page<Int, ExampleEntity2>(
            data = listOf(ExampleEntity2()), null, null
        ).toLoadResult()
    }

    override fun getRefreshKey(state: PagingState<Int, ExampleEntity2>): Int? {

        TODO("Not yet implemented")
    }
}
