
import dev.tesserakt.util.CommonPrefixStringPool
import dev.tesserakt.util.CommonPrefixStringPoolImpl
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class CommonPrefixStringPoolTest {

    @Test
    fun increaseInsertion() {
        // testing with increasing string length (1 -> 10 -> 100 ...)
        val collection = CommonPrefixStringPoolImpl()
        val handles = (0..1000).map { index ->
            val text = index.toString()
            val handle = collection.createHandle(text)
            assertEquals(text, handle.retrieve()) { "Collection structure:\n${collection}\n" }
            handle
        }
        println("Collection structure:\n$collection")
        handles.forEachIndexed { index, handle ->
            val text = index.toString()
            assertEquals(text, handle.retrieve()) { "Collection structure:\n${collection}\n" }
        }
    }

    @Test
    fun decreaseInsertion() {
        // testing with decreasing string length (1000 -> 100 -> 10 ...)
        val collection = CommonPrefixStringPoolImpl()
        val handles = (0..1000).reversed().map { index ->
            val text = index.toString()
            val handle = collection.createHandle(text)
            assertEquals(text, handle.retrieve()) { "Collection structure:\n${collection}\n" }
            handle
        }
        println("Collection structure:\n$collection")
        handles.forEachIndexed { index, handle ->
            val text = (1000 - index).toString()
            assertEquals(text, handle.retrieve()) { "Collection structure:\n${collection}\n" }
        }
    }

    @Test
    fun uriTest() {
        val uris = listOf(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            "http://purl.org/dc/elements/1.1/",
            "http://xmlns.com/foaf/0.1/",
            "http://www.w3.org/2000/01/rdf-schema#",
            "http://www.w3.org/2001/XMLSchema#",
            "http://www.w3.org/2002/07/owl#",
            "http://purl.org/vocab/vann",
            "http://web.resource.org/cc",
            "http://www.w3.org/2003/06/sw-vocab-status/ns#",
            "http://xmlns.com/wot/0.1/",
            "http://www.w3.org/2003/01/geo/wgs84_pos#",
            "http://www.w3.org/2001/02pd/rfc65#",
            "http://dublincore.org/2000/03/13-dcagent#",
            "http://www.w3.org/Addressing/schemes#",
        )
        val collection = CommonPrefixStringPool()
        val mapped = uris.associateWith {
            collection.createHandle(it)
        }
        println("Collection structure:\n$collection")
        mapped.forEach { (text, handle) ->
            assertEquals(text, handle.retrieve()) { "Collection structure:\n${collection}\n" }
        }
    }

    @Test
    fun specialStringTest() {
        val random = Random(0)
        val collection = CommonPrefixStringPoolImpl()
        val results = mutableMapOf<String, CommonPrefixStringPool.Handle>()
        repeat(1000) {
            val text = random.nextBytes(random.nextInt().absoluteValue.coerceAtMost(100)).decodeToString()
            val handle = collection.createHandle(text)
            val old = results.put(text, handle)
            assertTrue(old == null || old === handle)
            assertEquals(text, handle.retrieve()) { "Collection structure:\n${collection}\n" }
        }
        println("Collection structure:\n$collection")
        results.forEach { (text, handle) ->
            assertEquals(text, handle.retrieve()) { "Collection structure:\n${collection}\n" }
        }
    }

    private fun assertEquals(expected: String, actual: String, lazyMessage: () -> String) {
        if (expected != actual) {
            fail("Assertion failure - expected: $expected, actual: $actual\n${lazyMessage()}")
        }
    }

}
