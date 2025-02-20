package dev.tesserakt.rdf.serialization

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store

object NTriples {

    fun encodeToNTriples(store: Store): String {
        return store.joinToString("\n") { "${it.s.encoded()} ${it.p.encoded()} ${it.o.encoded()} ." }
    }

    private fun Quad.Term.encoded(): String {
        return when (this) {
            is Quad.BlankTerm -> "_:b$id"
            is Quad.Literal -> toString()
            is Quad.NamedTerm -> "<$value>"
        }
    }

}
