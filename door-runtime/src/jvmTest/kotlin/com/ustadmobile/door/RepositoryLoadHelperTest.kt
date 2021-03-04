package com.ustadmobile.door

import com.github.aakira.napier.DebugAntilog
import com.github.aakira.napier.Napier
import com.nhaarman.mockitokotlin2.*
import com.ustadmobile.door.DoorDatabaseRepository.Companion.STATUS_CONNECTED
import com.ustadmobile.door.RepositoryLoadHelper.Companion.STATUS_FAILED_NOCONNECTIVITYORPEERS
import com.ustadmobile.door.RepositoryLoadHelper.Companion.STATUS_LOADED_WITHDATA
import com.ustadmobile.door.RepositoryLoadHelper.Companion.STATUS_LOADING_CLOUD
import com.ustadmobile.door.RepositoryLoadHelper.Companion.STATUS_LOADING_MIRROR
import kotlinx.coroutines.*
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


class RepositoryLoadHelperTest  {

    data class DummyEntity(val uid: Long, val firstName: String, val lastName: String)

    @Before
    fun setup() {
        Napier.base(DebugAntilog())
    }

    @Test
    fun givenLoadSuccessful_whenDoRequestCalledAgain_thenShouldNotLoadAgain() {
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenReturn(DoorDatabaseRepository.STATUS_CONNECTED)
        }

        val invocationCount = AtomicInteger()
        val repoLoadHelper = RepositoryLoadHelper<DummyEntity>(mockRepository,
                lifecycleHelperFactory = mock {  }) {endpoint ->
            invocationCount.incrementAndGet()
            val entity = DummyEntity(100, "Bob", "Jones$endpoint")
            entity
        }

        val loadObserver = mock<DoorObserver<RepositoryLoadHelper.RepoLoadStatus>>{}
        repoLoadHelper.statusLiveData.observeForever(loadObserver)


        runBlocking {
            argumentCaptor<RepositoryLoadHelper.RepoLoadStatus>().apply {
                repoLoadHelper.doRequest()
                repoLoadHelper.doRequest()

                Assert.assertEquals("RepoLoadHelper only calls the actual request once if the " +
                        "request was successful" ,
                        1, invocationCount.get())

                verify(loadObserver, times(3)).onChanged(capture())
                Assert.assertEquals("First status value is 0 (didnt start)", 0,
                        firstValue.loadStatus)
                Assert.assertEquals("Second status is LOADING_FROM_CLOUD",
                        STATUS_LOADING_CLOUD, allValues[1].loadStatus)
                Assert.assertEquals("Third final value is LOADED_WITH_DATA",
                        STATUS_LOADED_WITHDATA, lastValue.loadStatus)
            }
        }

        repoLoadHelper.statusLiveData.removeObserver(loadObserver)
    }

    @Test
    fun givenLoadSuccessful_whenDoRequestCalledAgainLoadAgainParamSet_thenShouldLoadAgain() {
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenReturn(DoorDatabaseRepository.STATUS_CONNECTED)
        }

        val countDownLatch2 = CountDownLatch(2)
        val countDownLatch1 = CountDownLatch(1)
        val repoLoadHelper = RepositoryLoadHelper<DummyEntity>(mockRepository,
                lifecycleHelperFactory = mock {  }) {endpoint ->
            val entity = DummyEntity(100, "Bob", "Jones$endpoint")
            countDownLatch1.countDown()
            countDownLatch2.countDown()
            entity
        }

        var mockLiveData = repoLoadHelper.wrapLiveData(mock<DoorLiveData<DummyEntity>> {  })

        runBlocking {
            val mockLiveDataObserver = mock<DoorObserver<DummyEntity>>{}
            mockLiveData.observeForever(mockLiveDataObserver)

            //make sure that the first load happened before the second request
            countDownLatch1.await(5, TimeUnit.SECONDS)

            repoLoadHelper.doRequest(true, true)

            countDownLatch2.await(5, TimeUnit.SECONDS)
            Assert.assertEquals("LoadHelper ran twice when told to reload: count = 0", 0,
                    countDownLatch2.count)
        }
    }

    @Test
    fun givenLoadUnsuccessfulWithNoConnectivityAndLifeCycleIsNotActive_whenConnectivityResumed_thenShouldNotLoadAgain() {
        val currentConnectivityStatus = AtomicInteger(DoorDatabaseRepository.STATUS_DISCONNECTED)
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenReturn(currentConnectivityStatus.get())
            onBlocking { activeMirrors() }.thenReturn(listOf())
        }

        val entity = DummyEntity(100, "Bob", "Jones")
        val completableDeferred = CompletableDeferred<DummyEntity>()

        val repoLoadHelper = RepositoryLoadHelper<DummyEntity>(mockRepository,
                lifecycleHelperFactory = mock {  }) {endpoint ->
            if(currentConnectivityStatus.get() == DoorDatabaseRepository.STATUS_CONNECTED) {
                completableDeferred.complete(entity)
                entity
            }else {
                throw IOException("Mock IOException Not connected")
            }
        }

        val loadObserver = mock<DoorObserver<RepositoryLoadHelper.RepoLoadStatus>>{}
        repoLoadHelper.statusLiveData.observeForever(loadObserver)


        var mockLiveData = repoLoadHelper.wrapLiveData(mock<DoorLiveData<DummyEntity>> {  })

        runBlocking {
            try {
                repoLoadHelper.doRequest()
            }catch(e: Exception) {

            }

            currentConnectivityStatus.set(DoorDatabaseRepository.STATUS_CONNECTED)
            repoLoadHelper.onConnectivityStatusChanged(currentConnectivityStatus.get())
            val entityWithTimeout = withTimeoutOrNull(2000) { completableDeferred.await() }
            Assert.assertNull("When data is not being observed then a repeat request will " +
                    "not be made even when connectivity comes back",
                    entityWithTimeout)

            completableDeferred.cancel()

            argumentCaptor<RepositoryLoadHelper.RepoLoadStatus>().apply {
                verify(loadObserver, times(2)).onChanged(capture())
                Assert.assertEquals("First status value is 0 (didnt start)", 0,
                        firstValue.loadStatus)
                Assert.assertEquals("Final value is STATUS_FAILED_NOCONNECTIVITYORPEERS",
                        STATUS_FAILED_NOCONNECTIVITYORPEERS, lastValue.loadStatus)
            }

        }
    }

    @Test
    fun givenLoadUnsuccessfulWithNoConnectivityAndIsObserved_whenConnectivityResumed_thenShouldLoadAgain() {
        val currentConnectivityStatus = AtomicInteger(DoorDatabaseRepository.STATUS_DISCONNECTED)
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenAnswer {
                invocation -> currentConnectivityStatus.get()
            }
        }

        val entity = DummyEntity(100, "Bob", "Jones")
        val completableDeferred = CompletableDeferred<DummyEntity>()

        val repoLoadHelper = RepositoryLoadHelper<DummyEntity>(mockRepository,
                lifecycleHelperFactory = mock {  }) {endpoint ->
            if(currentConnectivityStatus.get() == DoorDatabaseRepository.STATUS_CONNECTED) {
                completableDeferred.complete(entity)
                entity
            }else {
                throw IOException("Mock IOException Not connected")
            }
        }

        val loadObserver = mock<DoorObserver<RepositoryLoadHelper.RepoLoadStatus>>{}
        repoLoadHelper.statusLiveData.observeForever(loadObserver)

        var liveData = mock<DoorLiveData<DummyEntity>> {  }

        val observer = mock<DoorObserver<DummyEntity>> {}
        runBlocking {
            try {
                //mark that there is an active observer - this will fail because it's still disconnected
                liveData = repoLoadHelper.wrapLiveData(liveData)
                liveData.observeForever(observer)

                //wait for this to fail before we rush down to change connectivity status
                verify(loadObserver, timeout(5000))
                        .onChanged(RepositoryLoadHelper.RepoLoadStatus(STATUS_FAILED_NOCONNECTIVITYORPEERS))
            } catch(e: Exception) {
                println(e)
                //do nothing
            }

            currentConnectivityStatus.set(DoorDatabaseRepository.STATUS_CONNECTED)
            repoLoadHelper.onConnectivityStatusChanged(DoorDatabaseRepository.STATUS_CONNECTED)

            val entityWithTimeout = withTimeout(5000) { completableDeferred.await() }

            Assert.assertEquals("After connectivity is restored and the obserer is active, " +
                    "the loadhelper automatically calls the request function", entity,
                    entityWithTimeout)

            argumentCaptor<RepositoryLoadHelper.RepoLoadStatus>() {
                verify(loadObserver, timeout(5000).atLeast(4))
                        .onChanged(capture())
                Assert.assertEquals("First status value is 0 (didnt start)", 0,
                        firstValue.loadStatus)
                Assert.assertEquals("Second value is STATUS_FAILED_NOCONNECTIVITYORPEERS after first fail",
                        STATUS_FAILED_NOCONNECTIVITYORPEERS, allValues[1].loadStatus)
                Assert.assertEquals("Third status is LOADING_FROM_CLOUD again when connectivity is restored",
                        STATUS_LOADING_CLOUD, allValues[2].loadStatus)
                Assert.assertEquals("Last value was loaded successfully", STATUS_LOADED_WITHDATA,
                        lastValue.loadStatus)

            }
        }
    }


    @Test
    fun givenLoadUnsuccessful_whenObservedAgainAndConnectivityAvailable_thenShouldLoadAgain() {
        val currentConnectivityStatus = AtomicInteger(DoorDatabaseRepository.STATUS_DISCONNECTED)
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenAnswer { invocation -> currentConnectivityStatus.get() }
            onBlocking { activeMirrors() }.thenReturn(listOf())
        }

        val entity = DummyEntity(100, "Bob", "Jones")
        val completableDeferred = CompletableDeferred<DummyEntity>()

        val loadHelperCallCount = AtomicInteger()
        val repoLoadHelper = RepositoryLoadHelper<DummyEntity>(mockRepository,
                lifecycleHelperFactory = mock {  }) {endpoint ->
            loadHelperCallCount.incrementAndGet()
            if(currentConnectivityStatus.get() == DoorDatabaseRepository.STATUS_CONNECTED) {
                completableDeferred.complete(entity)
                entity
            }else {
                throw IOException("Mock IOException Not connected")
            }
        }

        val loadObserver = mock<DoorObserver<RepositoryLoadHelper.RepoLoadStatus>>{}
        repoLoadHelper.statusLiveData.observeForever(loadObserver)

        val mockLiveData = mock<DoorLiveData<DummyEntity>> {  }
        val wrappedLiveData = repoLoadHelper.wrapLiveData(mockLiveData)
        val mockObserver = mock<DoorObserver<DummyEntity>> {}

        runBlocking {
            try {
                repoLoadHelper.doRequest()
            }catch(e: Exception) {
                //do nothing - this will fail as connectivity is off
            }

            val callCountBeforeConnectivityRestored = loadHelperCallCount.get()
            currentConnectivityStatus.set(DoorDatabaseRepository.STATUS_CONNECTED)
            repoLoadHelper.onConnectivityStatusChanged(DoorDatabaseRepository.STATUS_CONNECTED)
            delay(2000)
            val callCountAfterConnectivityRestored = loadHelperCallCount.get()

            //now observe it - this should trigger a call to try the request again
            wrappedLiveData.observeForever(mockObserver)
            val entityResult = withTimeout(2000) { completableDeferred.await() }


            Assert.assertEquals("When restoring connectivity with no active obserers there were" +
                    "no calls to the load function", callCountBeforeConnectivityRestored,
                    callCountAfterConnectivityRestored)

            Assert.assertEquals("When an observer is added after connectivity comes back, the request" +
                    "is automatically retried", entity, entityResult)

            verify(loadObserver, timeout(5000))
                    .onChanged(eq(RepositoryLoadHelper.RepoLoadStatus(STATUS_LOADED_WITHDATA)))
            argumentCaptor<RepositoryLoadHelper.RepoLoadStatus>() {
                verify(loadObserver, times(4)).onChanged(capture())
                Assert.assertEquals("First status value is 0 (didnt start)", 0,
                        firstValue.loadStatus)
                Assert.assertEquals("Third value is STATUS_FAILED_NOCONNECTIVITYORPEERS after first fail",
                        STATUS_FAILED_NOCONNECTIVITYORPEERS, allValues[1].loadStatus)
                Assert.assertEquals("Fourth status is LOADING_FROM_CLOUD again when connectivity is restored",
                        STATUS_LOADING_CLOUD, allValues[2].loadStatus)
                Assert.assertEquals("Last value was loaded successfully", STATUS_LOADED_WITHDATA,
                        lastValue.loadStatus)
            }
        }
    }

    @Test
    fun givenLoadUnsucessful_whenObservedAgainAndNoConnectivityAvailable_thenShouldNotLoadAgain() {
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenReturn(DoorDatabaseRepository.STATUS_DISCONNECTED)
            onBlocking { activeMirrors() }.thenReturn(listOf())
        }


        val loadHelperCallCount = AtomicInteger()
        val repoLoadHelper = RepositoryLoadHelper<DummyEntity>(mockRepository,
                lifecycleHelperFactory = mock {  }) {endpoint ->
            loadHelperCallCount.incrementAndGet()
            throw IOException("Mock IOException Not connected")
        }

        var liveData = mock<DoorLiveData<DummyEntity>> {}
        liveData = repoLoadHelper.wrapLiveData(liveData)
        val mockObserver = mock<DoorObserver<DummyEntity>> {}

        val loadObserver = mock<DoorObserver<RepositoryLoadHelper.RepoLoadStatus>>{}
        repoLoadHelper.statusLiveData.observeForever(loadObserver)

        runBlocking {
            try {
                repoLoadHelper.doRequest()
            } catch (e: Exception) {
                //do nothing - this will fail as connectivity is off
            }

            val callCountBeforeObserving = loadHelperCallCount.get()

            //now observe it
            liveData.observeForever(mockObserver)

            delay(2000)


            Assert.assertEquals("When adding an observer there are no further calls to the load" +
                    "function", callCountBeforeObserving, loadHelperCallCount.get())
            argumentCaptor<RepositoryLoadHelper.RepoLoadStatus>().apply {
                verify(loadObserver, times(2)).onChanged(capture())
                Assert.assertEquals("First value is 0 (didnt start)", 0, firstValue.loadStatus)
                Assert.assertEquals("Last value set is failed to load due to no connection",
                        STATUS_FAILED_NOCONNECTIVITYORPEERS, lastValue.loadStatus)
            }
        }
    }

    //TODO: this seems the wrong way around. If there is no lifecycle and it didn't load yet - we should retry
    @Test
    fun givenLoadUnuccessfulWithNoWrappedLiveData_whenConnectivityAvailable_thenShouldNotLoadAgain() {
        val currentConnectivityStatus = AtomicInteger(DoorDatabaseRepository.STATUS_DISCONNECTED)
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenAnswer { invocation -> currentConnectivityStatus.get() }
            onBlocking { activeMirrors() }.thenReturn(listOf())
        }

        val entity = DummyEntity(100, "Bob", "Jones")
        val completableDeferred = CompletableDeferred<DummyEntity>()

        val loadHelperCallCount = AtomicInteger()
        val repoLoadHelper = RepositoryLoadHelper<DummyEntity>(mockRepository,
                lifecycleHelperFactory = mock {  }) {endpoint ->
            loadHelperCallCount.incrementAndGet()
            if(currentConnectivityStatus.get() == DoorDatabaseRepository.STATUS_CONNECTED) {
                completableDeferred.complete(entity)
                entity
            }else {
                throw IOException("Mock IOException Not connected")
            }
        }

        val loadObserver = mock<DoorObserver<RepositoryLoadHelper.RepoLoadStatus>>{}
        repoLoadHelper.statusLiveData.observeForever(loadObserver)

        runBlocking {
            try {
                repoLoadHelper.doRequest()
            }catch(e: Exception) {
                //will fail because there is no connectivity
            }

            val callCountBeforeConnectivityRestored = loadHelperCallCount.get()
            currentConnectivityStatus.set(STATUS_CONNECTED)
            repoLoadHelper.onConnectivityStatusChanged(STATUS_CONNECTED)

            delay(2000)
            Assert.assertEquals("When there is no wrapped live data, the request is not retried when" +
                    "connectivity is restored", callCountBeforeConnectivityRestored, loadHelperCallCount.get())
            argumentCaptor<RepositoryLoadHelper.RepoLoadStatus>().apply {
                verify(loadObserver, times(2)).onChanged(capture())
                Assert.assertEquals("First value is 0 (didnt start)", 0,
                        firstValue.loadStatus)
                Assert.assertEquals("Last value set is failed to load due to no connection",
                        STATUS_FAILED_NOCONNECTIVITYORPEERS, lastValue.loadStatus)
            }
        }
    }


    @Test
    fun givenConnectivityAvailableAndMirrorAvailable_whenDoRequestCalled_thenWillUseMainEndpoint() {
        val mockCloudEndpoint = "http://cloudserver/endpoint"
        val mockMirrorEndpoint = "http://localhost:2000/proxy"
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenReturn(DoorDatabaseRepository.STATUS_CONNECTED)
            on {endpoint}.thenReturn(mockCloudEndpoint)
            onBlocking { activeMirrors() }.thenReturn(listOf(MirrorEndpoint(1, mockMirrorEndpoint, 100)))
        }

        val entity = DummyEntity(100, "Bob", "Jones")
        val endpointUsed = CompletableDeferred<String>()
        val repoLoadHelper = RepositoryLoadHelper<DummyEntity>(mockRepository,
                lifecycleHelperFactory = mock {  }) {endpoint ->
            endpointUsed.complete(endpoint)
            entity
        }

        val loadObserver = mock<DoorObserver<RepositoryLoadHelper.RepoLoadStatus>>{}
        repoLoadHelper.statusLiveData.observeForever(loadObserver)

        runBlocking {
            repoLoadHelper.doRequest()
            val endpointUsedRef = withTimeout(5000)  { endpointUsed.await() }
            Assert.assertEquals("Given connectivity is available and mirror is available " +
                    "repoloadhelper uses main endpoint", mockCloudEndpoint, endpointUsedRef)

            verify(loadObserver, timeout(5000L))
                    .onChanged(RepositoryLoadHelper.RepoLoadStatus(STATUS_LOADED_WITHDATA))
            argumentCaptor<RepositoryLoadHelper.RepoLoadStatus>().apply {
                verify(loadObserver, times(3)).onChanged(capture())
                Assert.assertEquals("First status value is 0 (didnt start)", 0,
                        firstValue.loadStatus)
                Assert.assertEquals("Second status is LOADING_FROM_CLOUD",
                        STATUS_LOADING_CLOUD, allValues[1].loadStatus)
                Assert.assertEquals("Third final value is LOADED_WITH_DATA",
                        STATUS_LOADED_WITHDATA, lastValue.loadStatus)
            }
        }
    }

    @Test
    fun givenNoConnectivityAvailableAndMirrorAvailable_whenDoRequestCalled_thenWillUseMirror() {
        val mockCloudEndpoint = "http://cloudserver/endpoint"
        val mockMirrorEndpoint = "http://localhost:2000/proxy"
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenReturn(DoorDatabaseRepository.STATUS_DISCONNECTED)
            on {endpoint}.thenReturn(mockCloudEndpoint)
            onBlocking { activeMirrors() }.thenReturn(listOf(MirrorEndpoint(1, mockMirrorEndpoint, 100)))
        }

        val entity = DummyEntity(100, "Bob", "Jones")
        val endpointUsed = CompletableDeferred<String>()
        val repoLoadHelper = RepositoryLoadHelper<DummyEntity>(mockRepository,
                lifecycleHelperFactory = mock {  }) {endpoint ->
            endpointUsed.complete(endpoint)
            entity
        }

        val loadObserver = mock<DoorObserver<RepositoryLoadHelper.RepoLoadStatus>>{}
        repoLoadHelper.statusLiveData.observeForever(loadObserver)

        runBlocking {
            repoLoadHelper.doRequest()
            val endpointUsedRef = withTimeout(5000)  { endpointUsed.await() }
            Assert.assertEquals("Given connectivity is not available and mirror is available " +
                    "repoloadhelper uses mirror endpoint", mockMirrorEndpoint, endpointUsedRef)
            verify(loadObserver, timeout(5000)).onChanged(RepositoryLoadHelper.RepoLoadStatus(STATUS_LOADED_WITHDATA))
            argumentCaptor<RepositoryLoadHelper.RepoLoadStatus>().apply {
                verify(loadObserver, times(3)).onChanged(capture())
                Assert.assertEquals("First status value is 0 (didnt start)", 0,
                        firstValue.loadStatus)
                Assert.assertEquals("Second status is LOADING_FROM_CLOUD",
                        STATUS_LOADING_MIRROR, allValues[1].loadStatus)
                Assert.assertEquals("Third final value is LOADED_WITH_DATA",
                        STATUS_LOADED_WITHDATA, lastValue.loadStatus)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun givenRequestUnsuccessfulAndDataIsObserved_whenNewMirrorAvailable_thenWillRetry() {
        val mockCloudEndpoint = "http://cloudserver/endpoint"
        val mockMirrorEndpoint = "http://localhost:2000/proxy"

        val currentConnectivityStatus = AtomicInteger(DoorDatabaseRepository.STATUS_DISCONNECTED)
        val currentActiveMirrorList = mutableListOf<MirrorEndpoint>()
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenAnswer { invocation -> currentConnectivityStatus.get() }
            on {endpoint}.thenReturn(mockCloudEndpoint)
            onBlocking { activeMirrors() }.thenAnswer { invocation -> currentActiveMirrorList }
        }

        val entity = DummyEntity(100, "Bob", "Jones")
        val endpointCompletableDeferred = CompletableDeferred<String>()
        val repoLoadHelper = RepositoryLoadHelper<DummyEntity>(mockRepository,
                lifecycleHelperFactory = mock {  }) {endpoint ->

            if(currentConnectivityStatus.get() == STATUS_CONNECTED || currentActiveMirrorList.isNotEmpty()) {
                endpointCompletableDeferred.complete(endpoint)
                entity
            }else {
                throw IOException("Mock offline and there is no mirror")
            }
        }

        val loadObserver = mock<DoorObserver<RepositoryLoadHelper.RepoLoadStatus>>{}
        repoLoadHelper.statusLiveData.observeForever(loadObserver)

        runBlocking {
            try {
                repoLoadHelper.doRequest()
            }catch(e: Exception) {
                //will fail
            }


            val endpointCompleteOnFirstRequest = endpointCompletableDeferred.isCompleted

            val mockLiveData = mock<DoorLiveData<DummyEntity>> {  }
            val wrappedLiveData = repoLoadHelper.wrapLiveData(mockLiveData)
            wrappedLiveData.observeForever(mock {})

            val newMirror = MirrorEndpoint(1, mockMirrorEndpoint, 100)
            currentActiveMirrorList.add(newMirror)

            repoLoadHelper.onNewMirrorAvailable(newMirror)

            val endpointUsed = withTimeout(5000) { endpointCompletableDeferred.await() }
            Assert.assertEquals("After a new mirror is available, and data is being observed, then " +
                    "the repoloadhelper automatically tries again using the new mirror",
                    mockMirrorEndpoint, endpointUsed)
            Assert.assertFalse("The repoloadhelper was not marked as complete when the request first loaded",
                    endpointCompleteOnFirstRequest)
            verify(loadObserver, timeout(5000)).onChanged(RepositoryLoadHelper.RepoLoadStatus(STATUS_LOADED_WITHDATA))
            argumentCaptor<RepositoryLoadHelper.RepoLoadStatus>().apply {
                verify(loadObserver, times(4)).onChanged(capture())
                Assert.assertEquals("First value is 0 (not started)", 0,
                        firstValue.loadStatus)
                Assert.assertEquals("Second value is STATUS_FAILED_NOCONNECTIVITYORPEERS",
                        STATUS_FAILED_NOCONNECTIVITYORPEERS, secondValue.loadStatus)
                Assert.assertEquals("Third value is LOADING_FROM_MIRROR", STATUS_LOADING_MIRROR,
                        thirdValue.loadStatus)
                Assert.assertEquals("Last status value set is LOADED_WITH_DATA", STATUS_LOADED_WITHDATA,
                        lastValue.loadStatus)
            }
        }
    }

    @Test
    fun givenRequestUnsuccessfulAndDataIsNotObserved_whenNewMirrorAvailable_thenWillDoNothing() {
        val mockCloudEndpoint = "http://cloudserver/endpoint"
        val mockMirrorEndpoint = "http://localhost:2000/proxy"

        val currentConnectivityStatus = AtomicInteger(DoorDatabaseRepository.STATUS_DISCONNECTED)
        val currentActiveMirrorList = mutableListOf<MirrorEndpoint>()
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenAnswer { invocation -> currentConnectivityStatus.get() }
            on {endpoint}.thenReturn(mockCloudEndpoint)
            onBlocking { activeMirrors() }.thenAnswer { invocation -> currentActiveMirrorList }
        }

        val entity = DummyEntity(100, "Bob", "Jones")
        val endpointCompletableDeferred = CompletableDeferred<String>()
        val loadFnCount = AtomicInteger()
        val repoLoadHelper = RepositoryLoadHelper<DummyEntity>(mockRepository,
                lifecycleHelperFactory = mock {  }) {endpoint ->

            loadFnCount.incrementAndGet()
            if(currentConnectivityStatus.get() == STATUS_CONNECTED || currentActiveMirrorList.isNotEmpty()) {
                endpointCompletableDeferred.complete(endpoint)
                entity
            }else {
                throw IOException("Mock offline and there is no mirror")
            }
        }

        val loadObserver = mock<DoorObserver<RepositoryLoadHelper.RepoLoadStatus>>{}
        repoLoadHelper.statusLiveData.observeForever(loadObserver)

        runBlocking {
            try {
                repoLoadHelper.doRequest()
            }catch(e: Exception) {
                //will fail
            }

            val loadFnCountBeforeMirror = loadFnCount.get()

            var liveData = mock<DoorLiveData<DummyEntity>> {  }
            liveData = repoLoadHelper.wrapLiveData(liveData)

            val newMirror = MirrorEndpoint(1, mockMirrorEndpoint, 100)
            currentActiveMirrorList.add(newMirror)

            repoLoadHelper.onNewMirrorAvailable(newMirror)

            val mirrorUsed = withTimeoutOrNull(5000) { endpointCompletableDeferred.await() }
            Assert.assertNull("After a new mirror is available, when data is not being " +
                    "observed the loadhelper will not try again",
                    mirrorUsed)
            Assert.assertEquals("DoRequest loader function has not been called again",
                    loadFnCountBeforeMirror, loadFnCount.get())
            argumentCaptor<RepositoryLoadHelper.RepoLoadStatus>().apply {
                verify(loadObserver, timeout(5000).times(2)).onChanged(capture())
                Assert.assertEquals("First callback value is 0 (not started)", 0,
                        firstValue.loadStatus)
                Assert.assertEquals("Last callback value is STATUS_FAILED_NOCONNECTIVITYORPEERS",
                        STATUS_FAILED_NOCONNECTIVITYORPEERS, lastValue.loadStatus)
            }
        }
    }


}
