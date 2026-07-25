
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.util.jsCastOrBail
import dev.tesserakt.util.jsExpect

/**
 * A thin wrapper for the [QuadJs] type. This is not a data class, as the copy method cannot be exposed
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("Quad")
class QuadJs(
    s: TermJs?,
    p: TermJs?,
    o: TermJs?,
    g: GraphTerm? = DefaultGraphTerm,
) {

    internal val value = Quad(
        s = s.jsExpect().repr.jsCastOrBail(),
        p = p.jsExpect().repr.jsCastOrBail(),
        o = o.jsExpect().repr.jsCastOrBail(),
        g = (g ?: DefaultGraphTerm).repr
    )

    override fun toString() = "$s $p $o $g"

    val s: TermJs get() = value.s.toTermJs()
    val p: TermJs get() = value.p.toTermJs()
    val o: TermJs get() = value.o.toTermJs()
    val g: GraphTerm get() = value.g.toTermJs()

}

// not exported as it references a KT-only type
fun QuadJs(quad: Quad): QuadJs {
    return QuadJs(
        s = quad.s.toTermJs(),
        p = quad.p.toTermJs(),
        o = quad.o.toTermJs(),
        g = quad.g.toTermJs(),
    )
}
