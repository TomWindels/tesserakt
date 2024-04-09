package tesserakt.rdf.serialization

import tesserakt.rdf.types.Store
import tesserakt.rdf.types.Triple

object NTriples {

    fun encodeToNTriples(store: Store): String {
        return store.joinToString("\n") { "${it.s.encoded()} ${it.p.encoded()} ${it.o.encoded()} ." }
    }

    private fun Triple.Term.encoded(): String {
        return when (this) {
            is Triple.BlankTerm -> "_:b$id"
            is Triple.Literal<*> -> toString()
            is Triple.NamedTerm -> "<$value>"
        }
    }

}
