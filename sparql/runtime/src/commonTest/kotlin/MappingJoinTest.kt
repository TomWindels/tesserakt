
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.sparql.runtime.evaluation.GlobalQueryContext
import dev.tesserakt.sparql.runtime.evaluation.Mapping
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MappingJoinTest {

    /* compatible checks */

    @Test
    fun compatible1() {
        val a = mappingOf("a" to "A".asNamedTerm())
        val b = mappingOf("a" to "A".asNamedTerm())
        assertContentEquals(a.asIterable(), a.join(b)!!.asIterable())
    }

    @Test
    fun compatible2() {
        val a = mappingOf("a" to "A".asNamedTerm())
        val b = mappingOf("a" to "A".asNamedTerm())
        assertContentEquals(a.asIterable(), Mapping(listOf(a.data!!, b.data!!))!!.asIterable())
    }

    @Test
    fun compatible3() {
        val a = mappingOf("a" to "A".asNamedTerm(), "b" to "A".asNamedTerm())
        val b = mappingOf("a" to "A".asNamedTerm(), "b" to "A".asNamedTerm())
        assertContentEquals(a.asIterable(), a.join(b)!!.asIterable())
    }

    @Test
    fun compatible4() {
        val a = mappingOf("a" to "A".asNamedTerm(), "b" to "A".asNamedTerm())
        val b = mappingOf("a" to "A".asNamedTerm(), "b" to "A".asNamedTerm())
        assertContentEquals(a.asIterable(), Mapping(listOf(a, b))!!.asIterable())
    }

    @Test
    fun compatible5() {
        val a = mappingOf("a" to "A".asNamedTerm(), "b" to "A".asNamedTerm())
        val b = mappingOf("a" to "A".asNamedTerm())
        assertContentEquals(a.asIterable(), a.join(b)!!.asIterable())
    }

    @Test
    fun compatible6() {
        val a = mappingOf("a" to "A".asNamedTerm(), "b" to "A".asNamedTerm())
        val b = mappingOf("a" to "A".asNamedTerm())
        assertContentEquals(a.asIterable(), Mapping(listOf(a, b))!!.asIterable())
    }

    @Test
    fun compatible7() {
        val a = mappingOf("a" to "A".asNamedTerm(), "b" to "A".asNamedTerm())
        val b = mappingOf("c" to "A".asNamedTerm())
        val r = mappingOf("a" to "A".asNamedTerm(), "b" to "A".asNamedTerm(), "c" to "A".asNamedTerm())
        assertContentEquals(r.asIterable(), a.join(b)!!.asIterable())
    }

    @Test
    fun compatible8() {
        val a = mappingOf("a" to "A".asNamedTerm(), "b" to "A".asNamedTerm())
        val b = mappingOf("c" to "A".asNamedTerm())
        val r = mappingOf("a" to "A".asNamedTerm(), "b" to "A".asNamedTerm(), "c" to "A".asNamedTerm())
        assertContentEquals(r.asIterable(), Mapping(listOf(a, b))!!.asIterable())
    }

    @Test
    fun compatible9() {
        val a = mappingOf("a" to "A".asNamedTerm(), "b" to "A".asNamedTerm())
        val b = mappingOf("c" to "A".asNamedTerm())
        val c = mappingOf("c" to "A".asNamedTerm(), "d" to "A".asNamedTerm(), "a" to "A".asNamedTerm())
        val r = mappingOf("a" to "A".asNamedTerm(), "b" to "A".asNamedTerm(), "c" to "A".asNamedTerm(), "d" to "A".asNamedTerm())
        assertContentEquals(r.asIterable(), Mapping(listOf(a, b, c))!!.asIterable())
    }

    /* incompatible checks */

    @Test
    fun incompatible1() {
        val a = mappingOf("a" to "A".asNamedTerm())
        val b = mappingOf("a" to "B".asNamedTerm())
        assertEquals(null, a.join(b))
    }

    @Test
    fun incompatible2() {
        val a = mappingOf("a" to "A".asNamedTerm())
        val b = mappingOf("a" to "B".asNamedTerm())
        assertEquals(-1, Mapping.count(listOf(a.data!!, b.data!!)))
    }

    @Test
    fun incompatible3() {
        val a = mappingOf("a" to "A".asNamedTerm(), "b" to "A".asNamedTerm())
        val b = mappingOf("a" to "B".asNamedTerm(), "b" to "A".asNamedTerm())
        assertEquals(null, a.join(b))
    }

    @Test
    fun incompatible4() {
        val a = mappingOf("a" to "A".asNamedTerm(), "b" to "A".asNamedTerm())
        val b = mappingOf("a" to "B".asNamedTerm(), "b" to "A".asNamedTerm())
        assertEquals(-1, Mapping.count(listOf(a.data!!, b.data!!)))
    }

    private fun mappingOf(vararg pairs: Pair<String, Quad.Term>) = dev.tesserakt.sparql.runtime.evaluation.mappingOf(GlobalQueryContext, *pairs)

}
