package com.ustadmobile.door

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.mockito.kotlin.*
import com.ustadmobile.door.DoorDatabaseRepository.Companion.STATUS_CONNECTED
import com.ustadmobile.door.RepositoryLoadHelper.Companion.STATUS_FAILED_NOCONNECTIVITYORPEERS
import com.ustadmobile.door.RepositoryLoadHelper.Companion.STATUS_LOADED_WITHDATA
import com.ustadmobile.door.RepositoryLoadHelper.Companion.STATUS_LOADING_CLOUD
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.json.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


class RepositoryLoadHelperTest  {

    data class DummyEntity(val uid: Long, val firstName: String, val lastName: String)

    private lateinit var repoConfig: RepositoryConfig

    private lateinit var httpClient: HttpClient

    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setup() {
        Napier.base(DebugAntilog())
        okHttpClient = OkHttpClient.Builder().build()
        httpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                gson()
            }
            install(HttpTimeout)

            engine {
                preconfigured = okHttpClient
            }
        }
        repoConfig = RepositoryConfig.repositoryConfig(Any(), "http://localhost:8089/",
            42L, "", httpClient, okHttpClient, kotlinx.serialization.json.Json { encodeDefaults = true })
    }

    @After
    fun after() {
        httpClient.close()
    }

    @Test
    fun givenLoadSuccessful_whenDoRequestCalledAgain_thenShouldNotLoadAgain() {
        val mockConfig = mock<RepositoryConfig> {
            on { endpoint }.thenReturn("http://localhost:8089/")
        }
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenReturn(STATUS_CONNECTED)
            on { config }.thenReturn(mockConfig)
        }

        val invocationCount = AtomicInteger()
        val repoLoadHelper = RepositoryLoadHelper(mockRepository,
                lifecycleHelperFactory = mock {  }) {endpoint ->
            invocationCount.incrementAndGet()
            val entity = DummyEntity(100, "Bob", "Jones$endpoint")
            entity
        }

        val loadObserver = mock<Observer<RepositoryLoadHelper.RepoLoadStatus>>{}
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
        val mockRepoConfig = mock<RepositoryConfig> {
            on { endpoint }.thenReturn("http://localhost:8089/")
        }
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenReturn(STATUS_CONNECTED)
            on { config }.thenReturn(mockRepoConfig)
        }

        val countDownLatch2 = CountDownLatch(2)
        val countDownLatch1 = CountDownLatch(1)
        val repoLoadHelper = RepositoryLoadHelper(mockRepository,
                lifecycleHelperFactory = mock {  }) {endpoint ->
            val entity = DummyEntity(100, "Bob", "Jones$endpoint")
            countDownLatch1.countDown()
            countDownLatch2.countDown()
            entity
        }

        val mockLiveData = repoLoadHelper.wrapLiveData(mock<LiveData<DummyEntity>> {  })

        runBlocking {
            val mockLiveDataObserver = mock<Observer<DummyEntity>>{}
            mockLiveData.observeForever(mockLiveDataObserver)

            //make sure that the first load happened before the second request
            countDownLatch1.await(5, TimeUnit.SECONDS)

            repoLoadHelper.doRequest(resetAttemptCount = true, runAgain = true)

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
            on { config }.thenReturn(repoConfig)
        }

        val entity = DummyEntity(100, "Bob", "Jones")
        val completableDeferred = CompletableDeferred<DummyEntity>()

        val repoLoadHelper = RepositoryLoadHelper(mockRepository,
                lifecycleHelperFactory = mock {  }, retryDelay = 500) {
            if(currentConnectivityStatus.get() == STATUS_CONNECTED) {
                completableDeferred.complete(entity)
                entity
            }else {
                throw IOException("Mock IOException Not connected")
            }
        }

        val loadObserver = mock<Observer<RepositoryLoadHelper.RepoLoadStatus>> { }
        repoLoadHelper.statusLiveData.observeForever(loadObserver)


        repoLoadHelper.wrapLiveData(mock<LiveData<DummyEntity>> {  })

        runBlocking {
            try {
                repoLoadHelper.doRequest()
            }catch(e: Exception) {

            }

            currentConnectivityStatus.set(STATUS_CONNECTED)
            repoLoadHelper.onConnectivityStatusChanged(currentConnectivityStatus.get())
            val entityWithTimeout = withTimeoutOrNull(2000) { completableDeferred.await() }
            Assert.assertNull("When data is not being observed then a repeat request will " +
                    "not be made even when connectivity comes back",
                    entityWithTimeout)

            completableDeferred.cancel()

            argumentCaptor<RepositoryLoadHelper.RepoLoadStatus>().apply {
                verify(loadObserver, atLeastOnce()).onChanged(capture())
                Assert.assertEquals("First status value is 0 (didnt start)", 0,
                        firstValue.loadStatus)
                Assert.assertEquals("Final value is STATUS_FAILED_NOCONNECTIVITYORPEERS",
                        STATUS_FAILED_NOCONNECTIVITYORPEERS, lastValue.loadStatus)
            }

        }
    }

    //Disabled 21/Dec/2021: This is not really used anymore. To be checked before merging to main
    //@Test
    fun givenLoadUnsuccessfulWithNoConnectivityAndIsObserved_whenConnectivityResumed_thenShouldLoadAgain() {
        val currentConnectivityStatus = AtomicInteger(DoorDatabaseRepository.STATUS_DISCONNECTED)
        val mockRepoConfig = mock<RepositoryConfig> {
            on { endpoint }.thenReturn("http://localhost:8089/")
        }
        val mockRepository = mock<DoorDatabaseRepository> {
            on { config }.thenReturn(mockRepoConfig)
            on {connectivityStatus}.thenAnswer {
                currentConnectivityStatus.get()
            }
        }

        val entity = DummyEntity(100, "Bob", "Jones")
        val completableDeferred = CompletableDeferred<DummyEntity>()

        val repoLoadHelper = RepositoryLoadHelper(mockRepository,
                lifecycleHelperFactory = mock {  }, retryDelay = 200) {
            if(currentConnectivityStatus.get() == STATUS_CONNECTED) {
                completableDeferred.complete(entity)
                entity
            }else {
                throw IOException("Mock IOException Not connected")
            }
        }

        val loadObserver = mock<Observer<RepositoryLoadHelper.RepoLoadStatus>>{}
        repoLoadHelper.statusLiveData.observeForever(loadObserver)

        var liveData = mock<LiveData<DummyEntity>> {  }

        val observer = mock<Observer<DummyEntity>> {}
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

            currentConnectivityStatus.set(STATUS_CONNECTED)
            repoLoadHelper.onConnectivityStatusChanged(STATUS_CONNECTED)

            val entityWithTimeout = withTimeout(5000) { completableDeferred.await() }

            Assert.assertEquals("After connectivity is restored and the obserer is active, " +
                    "the loadhelper automatically calls the request function", entity,
                    entityWithTimeout)

            argumentCaptor<RepositoryLoadHelper.RepoLoadStatus> {
                verify(loadObserver, timeout(5000).atLeast(4))
                        .onChanged(capture())
                Assert.assertEquals("First status value is 0 (didnt start)", 0,
                        firstValue.loadStatus)
                Assert.assertEquals("Second value is STATUS_FAILED_NOCONNECTIVITYORPEERS after first fail",
                        STATUS_FAILED_NOCONNECTIVITYORPEERS, allValues[2].loadStatus)
                Assert.assertEquals("Last value was loaded successfully", STATUS_LOADED_WITHDATA,
                        lastValue.loadStatus)

            }
        }
    }


    @Test
    fun givenLoadUnsuccessful_whenObservedAgainAndConnectivityAvailable_thenShouldLoadAgain() {
        val currentConnectivityStatus = AtomicInteger(DoorDatabaseRepository.STATUS_DISCONNECTED)
        val mockRepoConfig = mock<RepositoryConfig> {
            on { endpoint }.thenReturn("http://localhost:8089/")
        }
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenAnswer { currentConnectivityStatus.get() }
            on { config }.thenReturn(mockRepoConfig)
        }

        val entity = DummyEntity(100, "Bob", "Jones")
        val completableDeferred = CompletableDeferred<DummyEntity>()

        val loadHelperCallCount = AtomicInteger()
        val repoLoadHelper = RepositoryLoadHelper(mockRepository,
                lifecycleHelperFactory = mock {  }, retryDelay = 200) {
            loadHelperCallCount.incrementAndGet()
            if(currentConnectivityStatus.get() == STATUS_CONNECTED) {
                completableDeferred.complete(entity)
                entity
            }else {
                throw IOException("Mock IOException Not connected")
            }
        }

        val loadObserver = mock<Observer<RepositoryLoadHelper.RepoLoadStatus>>{}
        repoLoadHelper.statusLiveData.observeForever(loadObserver)

        val mockLiveData = mock<LiveData<DummyEntity>> {  }
        val wrappedLiveData = repoLoadHelper.wrapLiveData(mockLiveData)
        val mockObserver = mock<Observer<DummyEntity>> {}

        runBlocking {
            try {
                repoLoadHelper.doRequest()
            }catch(e: Exception) {
                //do nothing - this will fail as connectivity is off
            }

            val callCountBeforeConnectivityRestored = loadHelperCallCount.get()
            currentConnectivityStatus.set(STATUS_CONNECTED)
            repoLoadHelper.onConnectivityStatusChanged(STATUS_CONNECTED)
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
            argumentCaptor<RepositoryLoadHelper.RepoLoadStatus> {
                verify(loadObserver, atLeast(4)).onChanged(capture())
                Assert.assertEquals("First status value is 0 (didnt start)", 0,
                        firstValue.loadStatus)
                Assert.assertEquals("Second value is STATUS_FAILED_NOCONNECTIVITYORPEERS after first fail",
                        STATUS_FAILED_NOCONNECTIVITYORPEERS, allValues[2].loadStatus)
                Assert.assertEquals("Last value was loaded successfully", STATUS_LOADED_WITHDATA,
                        lastValue.loadStatus)
            }
        }
    }

    //TODO: this seems the wrong way around. If there is no lifecycle and it didn't load yet - we should retry
    @Test
    fun givenLoadUnuccessfulWithNoWrappedLiveData_whenConnectivityAvailable_thenShouldNotLoadAgain() {
        val currentConnectivityStatus = AtomicInteger(DoorDatabaseRepository.STATUS_DISCONNECTED)
        val mockRepository = mock<DoorDatabaseRepository> {
            on {connectivityStatus}.thenAnswer { currentConnectivityStatus.get() }
            on { config }.thenReturn(repoConfig)
        }

        val entity = DummyEntity(100, "Bob", "Jones")
        val completableDeferred = CompletableDeferred<DummyEntity>()

        val loadHelperCallCount = AtomicInteger()
        val repoLoadHelper = RepositoryLoadHelper(mockRepository,
                lifecycleHelperFactory = mock {  }, retryDelay = 200) {
            loadHelperCallCount.incrementAndGet()
            if(currentConnectivityStatus.get() == STATUS_CONNECTED) {
                completableDeferred.complete(entity)
                entity
            }else {
                throw IOException("Mock IOException Not connected")
            }
        }

        val loadObserver = mock<Observer<RepositoryLoadHelper.RepoLoadStatus>>{}
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
                verify(loadObserver, atLeastOnce()).onChanged(capture())
                Assert.assertEquals("First value is 0 (didnt start)", 0,
                        firstValue.loadStatus)
                Assert.assertEquals("Last value set is failed to load due to no connection",
                        STATUS_FAILED_NOCONNECTIVITYORPEERS, lastValue.loadStatus)
            }
        }
    }


}
