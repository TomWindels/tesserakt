import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.types.ConcurrentMutableStore
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.coroutines.*
import dev.tesserakt.sparql.get
import dev.tesserakt.util.printerrln
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTime

class FlowTest {

    private val POINT_COUNT = 50

    private val data = buildStore {
        val ex = prefix("ex", "http://example.org/")
        ex("test") has type being ex("Test")
        repeat(POINT_COUNT) { i ->
            ex("test") has ex("data") being ex("point_$i")
            ex("point_$i") has ex("value") being i
        }
    }

    private val LONG_QUERY = Query.Select(
        """
            PREFIX ex: <http://example.org/>
            SELECT ?value WHERE {
                ?s a ex:Test .
                ?s ex:data ?point .
                ?point ex:value ?value .
            }
        """
    )

    @OptIn(DelicateStoreApi::class)
    @Test
    fun storeObservationTest() = runTest {
        val store = ConcurrentMutableStore()
        val scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
        val states = store
            .observeAsFlow()
            .channelIn(scope)
        scope.launch {
            yield()
            data.toSet().forEach { quad ->
                store.add(quad)
                yield()
                assertEquals(Delta.Addition(quad), states.receive(1.seconds))
            }
            data.toSet().forEach { quad ->
                store.remove(quad)
                yield()
                assertEquals(Delta.Deletion(quad), states.receive(1.seconds))
            }
        }.join()
        scope.cancel()
    }

    @OptIn(DelicateStoreApi::class)
    @Test
    fun storeInitialStateTest() = runTest {
        val store = ConcurrentMutableStore()
        val scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
        val first = data.first()
        store.add(first)
        val state = store
            .observeAsFlow()
            .stateIn(scope, SharingStarted.Eagerly, null)
        val expected = Delta.Addition(first)
        withContext(scope.coroutineContext) {
            try {
                withTimeout(50.milliseconds) {
                    state.first { it == expected }
                }
            } catch (t: TimeoutCancellationException) {
                fail("Did not get the first state in time", t)
            }
        }
        scope.cancel()
    }

    @Test
    fun deltaTest() = runTest {
        val query = Query.Select("SELECT * WHERE { ?s !a ?o }")

        var collected = 0
        data
            .toSet()
            .asFlow()
            .query(query)
            .collect { delta ->
                ++collected
                assertTrue(
                    message = "Check failed for result $delta",
                    block = {
                        delta is Delta.Addition &&
                        delta.value["o"].let { term ->
                            term != null && (
                                term.value.matches(Regex("http://example\\.org/point_[0-9]+")) ||
                                term.value.toIntOrNull() != null
                            )
                        }
                    }
                )
            }
        // all but the first triple should be collected
        assertEquals(collected, data.size - 1)
    }

    @Test
    fun mutableStoreTestBuildup() = runTest {
        val store = ConcurrentMutableStore()
        var collected = 0
        var start: TimeSource.Monotonic.ValueTimeMark? = null
        var end: TimeSource.Monotonic.ValueTimeMark? = null
        val dispatcher = Dispatchers.Default.limitedParallelism(2)

        val job = launch(dispatcher) {
            withTimeout(1.seconds) {
                store
                    .queryAsFlow(LONG_QUERY)
                    .onStart { start = TimeSource.Monotonic.markNow() }
                    .collect {
                        end = TimeSource.Monotonic.markNow()
                        ++collected
                    }
            }
        }

        val insertion = async(dispatcher) {
            measureTime {
                store.addAll(data.toSet())
            }
        }.await()

        job.join()
        // if this check passes, we can guarantee time marks have been set
        assertEquals(POINT_COUNT, collected)
        val processing = end!! - start!!
        assertTrue(
            processing > insertion,
            "Insertion took longer than processing!\n * Processing: $processing\n * Insertion: $insertion"
        )
    }

    @Test
    fun mutableStoreTestDirectBuildupAndBreakdown() = runTest {
        val scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
        val store = ConcurrentMutableStore()
        var remaining = 0
        var start: TimeSource.Monotonic.ValueTimeMark? = null
        var end: TimeSource.Monotonic.ValueTimeMark? = null

        val job1 = scope.launch {
            try {
                withTimeout(1.seconds) {
                    store
                        .queryAsFlow(LONG_QUERY)
                        .onStart { start = TimeSource.Monotonic.markNow() }
                        .collect {
                            end = TimeSource.Monotonic.markNow()
                            if (it is Delta.Addition) {
                                ++remaining
                            } else {
                                --remaining
                            }
                        }
                    fail("Reached the end of collection unexpectedly!")
                }
            } catch (t: TimeoutCancellationException) {
                // collection halted correctly
            } catch (t: Throwable) {
                fail("Caught ${t::class.simpleName} during collection!", t)
            }
        }
        var insertion: Duration? = null
        var deletion: Duration? = null
        val job2 = scope.launch {
            yield()
            insertion = measureTime {
                store.addAll(data.toSet())
            }
            yield()
            deletion = measureTime {
                store.removeAll(data.toSet())
            }
        }
        job2.join()
        job1.join()
        scope.cancel()
        // if these checks pass, we can guarantee time marks have been set
        assertEquals(0, remaining)
        assertTrue(end != null, "No items were collected, which is not expected behaviour!")
        val processing = end!! - start!!
        val manipulation = insertion!! + deletion!!
        assertTrue(
            processing > manipulation,
            "Store manipulation took longer than processing!\n * Processing: $processing\n * Insertion: $insertion\n * Deletion: $deletion"
        )
    }

    @Test
    fun dataFlowTestSlowConsumer(): TestResult {
        var quadEventCount = 0
        var resultCount = 0

        return consumerTest(
            consumer = {
                try {
                    withTimeout(1.seconds) {
                        flow { data.toSet().forEach { emit(it); delay(1) } }
                            .onEach { ++quadEventCount }
                            .query(LONG_QUERY)
                            .collect { ++resultCount }
                        // it should reach this line, the timeout should not be hit!
                    }
                } catch (t: TimeoutCancellationException) {
                    fail("Reached the timeout unexpectedly!", t)
                } catch (t: Throwable) {
                    fail("Caught ${t::class.simpleName} during collection!", t)
                }
            },
            finalize = {
                // = # of quad additions + # quad of deletions
                assertEquals(data.size, quadEventCount, "Not all quad events have propagated!")
                assertEquals(POINT_COUNT, resultCount, "Not all results have been obtained!")
            }
        )
    }

    @OptIn(DelicateStoreApi::class)
    @Test
    fun mutableStoreTestObservedBuildupAndBreakdownFastConsumer(): TestResult {
        val store = ConcurrentMutableStore()

        var quadEventCount = 0

        return producerConsumerTest(
            producer = {
                data.forEach {
                    store.add(it)
                }
                data.forEach {
                    store.remove(it)
                }
            },
            consumer = {
                try {
                    withTimeout(1.seconds) {
                        store
                            .observeAsFlow()
                            .collect { ++quadEventCount }
                        fail("Reached the end of collection unexpectedly!")
                    }
                } catch (t: TimeoutCancellationException) {
                    // collection halted correctly
                } catch (t: Throwable) {
                    fail("Caught ${t::class.simpleName} during collection!", t)
                }
            },
            finalize = {
                // = # of quad additions + # quad of deletions
                assertEquals(data.size * 2, quadEventCount, "Not all quad events have propagated!")
            }
        )
    }

    @OptIn(DelicateStoreApi::class)
    @Test
    fun mutableStoreTestObservedBuildupAndBreakdownSlowConsumer(): TestResult {
        val store = ConcurrentMutableStore()

        var insertion: Duration? = null
        var deletion: Duration? = null

        var totalQueryResults = 0
        var quadEventCount = 0

        var start: TimeSource.Monotonic.ValueTimeMark? = null
        var end: TimeSource.Monotonic.ValueTimeMark? = null

        return producerConsumerTest(
            producer = {
                insertion = measureTime {
                    data.forEach {
                        store.add(it)
                    }
                }
                deletion = measureTime {
                    data.forEach {
                        store.remove(it)
                    }
                }
            },
            consumer = {
                try {
                    withTimeout(1.seconds) {
                        store
                            .observeAsFlow()
                            .onEach { ++quadEventCount }
                            .query(LONG_QUERY)
                            .onStart { start = TimeSource.Monotonic.markNow() }
                            .collect {
                                end = TimeSource.Monotonic.markNow()
                                if (it is Delta.Addition) {
                                    ++totalQueryResults
                                } else {
                                    --totalQueryResults
                                }
                            }
                        fail("Reached the end of collection unexpectedly!")
                    }
                } catch (t: TimeoutCancellationException) {
                    // collection halted correctly
                } catch (t: Throwable) {
                    fail("Caught ${t::class.simpleName} during collection!", t)
                }
            },
            finalize = {
                // if these checks pass, we can guarantee time marks have been set
                // = # of quad additions + # quad of deletions
                assertEquals(data.size * 2, quadEventCount, "Not all quad events have propagated!")
                // all quads processed mean no results should be left, as the final input is empty
                assertEquals(
                    0,
                    totalQueryResults,
                    "Not all results have been cleared properly! Did all quads finish processing?"
                )
                // if at least one item was processed before the timeout was reached, we have an end value
                assertTrue(end != null, "No items were collected, which is not expected behaviour!")
                val querying = end!! - start!!
                val ingestion = insertion!! + deletion!!
                assertTrue(
                    querying > ingestion,
                    "Store ingestion took longer than querying!\n * Querying: $querying\n * Insertion: $insertion\n * Deletion: $deletion"
                )
            }
        )
    }

    /* helpers */

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private fun consumerTest(
        consumer: suspend CoroutineScope.() -> Unit,
        finalize: suspend CoroutineScope.() -> Unit = {},
    ) = runTest {
        val testThread = createContext("TestThread")

        val testJob = launch(
            context = testThread.dispatcher,
            start = CoroutineStart.LAZY,
            block = consumer
        )

        testJob.join()

        testThread.close()

        finalize()
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private fun producerConsumerTest(
        producer: suspend CoroutineScope.() -> Unit,
        consumer: suspend CoroutineScope.() -> Unit,
        finalize: suspend CoroutineScope.() -> Unit = {},
    ) = runTest {
        val consumerThread = createContext("ConsumerThread")
        val producerThread = createContext("ProducerThread")

        val consumerJob = launch(
            context = consumerThread.dispatcher,
            start = CoroutineStart.UNDISPATCHED,
            block = consumer
        )
        val producerJob = launch(
            context = producerThread.dispatcher,
            start = CoroutineStart.LAZY,
            block = producer
        )

        producerJob.start()
        producerJob.join()
        consumerJob.join()

        producerThread.close()
        consumerThread.close()

        finalize()
    }

    private fun <T> Flow<T>.channelIn(scope: CoroutineScope): Channel<T> {
        val result = Channel<T>()
        scope.launch {
            collect {
                result.send(it)
            }
        }
        return result
    }

    private suspend fun <T> Channel<T>.receive(timeout: Duration): T? {
        return try {
            withTimeout(timeout) {
                receive()
            }
        } catch (t: TimeoutCancellationException) {
            printerrln("Timeout was reached!\n${t.stackTraceToString()}")
            null
        }
    }

}

expect class DispatchHelper {
    val dispatcher: CoroutineDispatcher
    fun close()
}

expect fun createContext(name: String): DispatchHelper
