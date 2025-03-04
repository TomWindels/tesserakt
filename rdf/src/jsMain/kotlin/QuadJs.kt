
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.util.jsCastOrBail
import dev.tesserakt.util.jsExpect

/**
 * A thin wrapper for the [Quad] type. This is not a data class, as the copy method cannot be exposed
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("Quad")
class QuadJs(
    s: TermJs? = undefined,
    p: TermJs? = undefined,
    o: TermJs? = undefined,
    g: GraphJs = graph(),
) {

    @JsName("Term")
    class TermJs internal constructor(internal val value: Quad.Term) {

        override fun hashCode(): Int {
            return value.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            return value.equals(other)
        }

        override fun toString(): String {
            return value.toString()
        }

    }

    @JsName("Graph")
    class GraphJs internal constructor(internal val value: Quad.Graph) {

        override fun hashCode(): Int {
            return value.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            return value.equals(other)
        }

        override fun toString(): String {
            return value.toString()
        }

    }

    private val value = Quad(
        s = s.jsExpect().value,
        p = p.jsExpect().value.jsCastOrBail(),
        o = o.jsExpect().value,
        g = g.value
    )

    val s: TermJs get() = TermJs(value.s)
    val p: TermJs get() = TermJs(value.p)
    val o: TermJs get() = TermJs(value.o)
    val g: GraphJs get() = GraphJs(value.g)

    internal fun unwrap() = value

    companion object Builder {

        // using JsStatic, as `Builder` can be omitted
        @OptIn(ExperimentalJsStatic::class)
        @JsStatic
        fun iri(value: String? = undefined) = TermJs(value.jsExpect().asNamedTerm())

        @OptIn(ExperimentalJsStatic::class)
        @JsStatic
        fun literal(value: Any? = undefined) = TermJs(value.jsExpect().asLiteralTerm())

        @OptIn(ExperimentalJsStatic::class)
        @JsStatic
        fun graph(value: String? = undefined) = GraphJs(value?.asNamedTerm() ?: Quad.DefaultGraph)

    }

}
