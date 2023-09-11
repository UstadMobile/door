package com.ustadmobile.door.http

import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.ext.weakMapOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Helper class that is used by DAO repositories to manage returned flows.
 */
class RepoDaoFlowHelper(
    @Suppress("unused") // reserved for future use - will be used to control http timeouts etc in future
    private val repo: DoorDatabaseRepository,
) {

    /**
     * Note: the weakmap is required to
     */
    private val flowToStatusMap: MutableMap<Flow<*>, Flow<LoadingState>> = weakMapOf()

    /**
     * This is used by generated code to create an offline-first flow where the flow will immediately load from the
     * local database. Collecting the flow will trigger an http request in the background to pull updates from the
     * server (if any). This will run an insert, which would trigger the flow to update with the new data automatically.
     *
     * @param dbFlow the flow from the database DAO
     * @param onMakeHttpRequest a function that will make the http request to fetch replicate entities and insert them
     *        into the database
     */
    fun <T> asRepoFlow(
        dbFlow: Flow<T>,
        onMakeHttpRequest: suspend () -> Unit,
    ): Flow<T> {
        val statusFlow = MutableStateFlow(LoadingState())

        val wrappedFlow = dbFlow.onStart {
            val requestScope = CoroutineScope(currentCoroutineContext() + Job())

            requestScope.launch {
                try {
                    statusFlow.update { prev -> prev.copy(LoadingState.Status.LOADING) }
                    onMakeHttpRequest()
                    statusFlow.update { prev -> prev.copy(LoadingState.Status.DONE) }
                }catch(e: Exception) {
                    statusFlow.update { prev -> prev.copy(LoadingState.Status.FAILED) }
                }
            }
        }
        flowToStatusMap[wrappedFlow] = statusFlow

        return wrappedFlow
    }

    /**
     *
     */
    fun httpStatusOf(flow: Flow<*>): Flow<LoadingState>? {
        return flowToStatusMap[flow]
    }

}