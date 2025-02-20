package dev.tesserakt.rdf.n3

import kotlin.jvm.JvmInline

@ExperimentalN3Api
data class Quad(
    val s: Term,
    val p: Term,
    val o: Term
) {

    sealed interface Term {
        @JvmInline value class RdfTerm(val term: dev.tesserakt.rdf.types.Quad.Term): Term {
            override fun toString() = term.toString()
        }

        @JvmInline value class StatementsList(val statements: Store): Term {
            override fun toString() = statements.joinToString(" . ", prefix = "{", postfix = "}")
        }
    }

    override fun toString() = "$s $p $o"

}
