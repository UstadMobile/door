package com.ustadmobile.door.http

import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.ext.weakMapOf
import com.ustadmobile.door.flow.FlowLoadingState
import com.ustadmobile.door.util.IWeakMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Helper class that is used by DAO repositories to manage returned flows.
 */
class RepoDaoFlowHelper(
    @Suppress("unused") // reserved for future use - will be used to control http timeouts etc in future
    private val repo: DoorDatabaseRepository,
) {

    /**
     * Note: The WeakMap is required because we want to ensure that we keep one status flow per database flow as long as
     * the database flow is in memory
     */
    private val flowToStatusMap: IWeakMap<Flow<*>, Flow<FlowLoadingState>> = weakMapOf()

    /**
     * This is used by generated code to create an offline-first flow where the flow will immediately load from the
     * local database. Collecting the flow will trigger a http request in the background to pull updates from the
     * server (if any). This will run an insert, which would trigger the flow to update with the new data automatically.
     *
     * @param dbFlow the flow from the database DAO
     * @param onMakeHttpRequest a (generated) function that will make the http request to fetch replicate entities and
     *        insert them into the database
     */
    fun <T> asRepoFlow(
        dbFlow: Flow<T>,
        onMakeHttpRequest: suspend () -> Unit,
    ): Flow<T> {
        val statusFlow = MutableStateFlow(FlowLoadingState())

        val wrappedFlow = dbFlow.onStart {
            val requestScope = CoroutineScope(currentCoroutineContext() + Job())

            requestScope.launch {
                try {
                    statusFlow.update { prev -> prev.copy(FlowLoadingState.Status.LOADING) }
                    onMakeHttpRequest()
                    statusFlow.update { prev -> prev.copy(FlowLoadingState.Status.DONE) }
                }catch(e: Exception) {
                    statusFlow.update { prev -> prev.copy(FlowLoadingState.Status.FAILED) }
                }
            }
        }
        flowToStatusMap[wrappedFlow] = statusFlow

        return wrappedFlow
    }

    /**
     *
     */
    fun httpStatusOf(flow: Flow<*>): Flow<FlowLoadingState>? {
        return flowToStatusMap[flow]
    }

}