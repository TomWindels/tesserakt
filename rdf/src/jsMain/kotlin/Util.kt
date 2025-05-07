import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.util.mapToArray

/* A series of helpers to work with internal types inside of Kotlin codebases, so the JS API does not expose them directly */

fun MutableStoreJs.unwrap() = this.store

fun Collection<Quad>.toJsMutableStore() = MutableStoreJs(
    quads = mapToArray { it.toJsQuad() }
)

fun Store.toJsMutableStore() = MutableStoreJs(
    quads = toSet().mapToArray { it.toJsQuad() }
)


fun QuadJs.unwrap() = this.value

fun Quad.Term.toJsTerm() = QuadJs.TermJs(this)

fun Quad.Graph.toJsGraph() = QuadJs.GraphJs(this)

fun Quad.toJsQuad() = QuadJs(
    s = s.toJsTerm(),
    p = p.toJsTerm(),
    o = o.toJsTerm(),
    g = g.toJsGraph(),
)
