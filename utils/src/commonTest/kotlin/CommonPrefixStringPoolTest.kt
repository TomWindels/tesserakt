
import dev.tesserakt.util.CommonPrefixStringPool
import dev.tesserakt.util.CommonPrefixStringPoolImpl
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class CommonPrefixStringPoolTest {

    @Test
    fun smallIncreaseInsertion() {
        increaseInsertion(1)
    }

    @Test
    fun mediumIncreaseInsertion() {
        increaseInsertion(3)
    }

    @Test
    fun maxSizeIncreaseInsertion() {
        increaseInsertion(Int.MAX_VALUE)
    }

    fun increaseInsertion(size: Int) {
        // testing with increasing string length (1 -> 10 -> 100 ...)
        val collection = CommonPrefixStringPoolImpl(size)
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
    fun smallDecreaseInsertion() {
        decreaseInsertion(1)
    }

    @Test
    fun mediumDecreaseInsertion() {
        decreaseInsertion(3)
    }

    @Test
    fun maxSizeDecreaseInsertion() {
        decreaseInsertion(Int.MAX_VALUE)
    }

    fun decreaseInsertion(size: Int) {
        // testing with decreasing string length (1000 -> 100 -> 10 ...)
        val collection = CommonPrefixStringPoolImpl(size)
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
    fun smallUriTest() {
        uriTest(1)
    }

    @Test
    fun mediumUriTest() {
        uriTest(5)
    }

    @Test
    fun maxSizeUriTest() {
        uriTest(Int.MAX_VALUE)
    }

    fun uriTest(size: Int) {
        val uris = listOf(
            "abc",
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
        val collection = CommonPrefixStringPool(size)
        val mapped = uris.associateWith {
            collection.createHandle(it)
        }
        println("Collection structure:\n$collection")
        mapped.forEach { (text, handle) ->
            assertEquals(text, handle.retrieve()) { "Collection structure:\n${collection}\n" }
        }
    }

    @Test
    fun smallSpecialStringTest() {
        specialStringTest(1)
    }

    @Test
    fun mediumSpecialStringTest() {
        specialStringTest(3)
    }

    @Test
    fun maxSizeSpecialStringTest() {
        specialStringTest(Int.MAX_VALUE)
    }

    fun specialStringTest(size: Int) {
        val random = Random(0)
        val collection = CommonPrefixStringPoolImpl(size)
        val results = mutableMapOf<String, CommonPrefixStringPool.Handle>()
        repeat(1000) {
            val bytes = random.nextBytes(100 + random.nextInt() % 50)
            repeat(bytes.size) {
                // 'a' - 'z'
                bytes[it] = (bytes[it] % 12 + 109).toByte()
            }
            val text = bytes.decodeToString()
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
