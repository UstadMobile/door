package com.ustadmobile.door.flow

import com.ustadmobile.door.RepositoryFlowLoadingStatusProvider
import com.ustadmobile.door.http.RepositoryDaoWithFlowHelper
import com.ustadmobile.door.http.ValueAndLoadingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * Given a Flow that has been returned by a repository DAO, combine it with the loading state to create a flow that
 * contains both the value and the loading state.
 */
fun <T> Flow<T>.combineWithLoadingState(
    repositoryDao: RepositoryFlowLoadingStatusProvider
): Flow<ValueAndLoadingState<T>> {
    val loadingStateFlow: Flow<FlowLoadingState?> = (repositoryDao as? RepositoryDaoWithFlowHelper)?.repoDaoFlowHelper?.httpStatusOf(this)
        ?: flowOf<FlowLoadingState?>(null)
    return this.combine(loadingStateFlow) { value, loadingState ->
        ValueAndLoadingState(value, loadingState)
    }
}

/**
 * Get a flow that contains both the value and the loading state e.g.
 * myRepo.daoName.repoFlowWithLoadingState { it.findEntityAsFlow(paramName) }.collect { valueAndState ->
 *
 * }
 */
fun <D: RepositoryFlowLoadingStatusProvider, T> D.repoFlowWithLoadingState(
    flow: (D) -> Flow<T>
): Flow<ValueAndLoadingState<T>> {
    val flowVal: Flow<T> = flow(this)
    return flowVal.combineWithLoadingState(this)
}


