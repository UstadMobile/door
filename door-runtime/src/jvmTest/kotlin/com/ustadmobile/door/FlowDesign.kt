package com.ustadmobile.door

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.internal.toImmutableMap
import org.junit.Test

class FlowDesign {

    @OptIn(ExperimentalCoroutinesApi::class)
    class SomeAbstractFlow<T>(
        private val dbFlow: Flow<T>
    ): AbstractFlow<T>() {

        override suspend fun collectSafely(collector: FlowCollector<T>) {
            collector.emitAll(dbFlow)
        }
    }

    data class Entity(
        val uid: Long
    )

    suspend fun httpQuery() {
        println("start request")
        delay(150)
        println("complete request")
    }

    data class ActiveRequest(
        val flow: Flow<*>,
        val status: Int,
    )

    val activeRequests = MutableStateFlow<Map<Flow<*>, ActiveRequest>>(emptyMap())


    suspend fun someQueryFn() : Flow<List<Entity>>{
        val returnedFlow = CompletableDeferred<Flow<List<Entity>>>()
        val dbFlow = flow<List<Entity>> {
            for(i in 1..10) {
                emit((0.. i).map { Entity(it.toLong()) })
                println("db emit $i")
                delay(100)
            }
        }.onStart {
            //if wait for request to complete... this can run the request / insert into db accordingly.
            val requestScope = CoroutineScope(currentCoroutineContext() + Job())
            val myFlow = returnedFlow.await()
            activeRequests.update { prev ->
                buildMap {
                    putAll(prev)
                    put(myFlow, ActiveRequest(myFlow, 1))
                }
            }

            requestScope.launch {
                httpQuery()
                activeRequests.update { prev ->
                    prev.toMutableMap().also {
                        it.remove(myFlow)
                    }.toImmutableMap()
                }

                try {
                    awaitCancellation()
                }catch(e: CancellationException) {
                    //remove from active requests
                }
            }
        }

        returnedFlow.complete(dbFlow)


        return dbFlow
    }


    @Test
    fun someFlowWithTimeout() {
        runBlocking {
            withTimeoutOrNull(100) {
                someQueryFn().collect{
                    println("received $it")
                }
            }
            println("cancel!")

            delay(1000)
        }

    }

    @Test
    fun someFlowStuff() {
        runBlocking {
            someQueryFn().collect{
                println("received $it")
            }


        }
    }

}