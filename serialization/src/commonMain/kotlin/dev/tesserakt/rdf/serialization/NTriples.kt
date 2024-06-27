package dev.tesserakt.rdf.serialization

import dev.tesserakt.rdf.serialization.NTriples.decodeSingleLine
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store

object NTriples {

    fun encodeToNTriples(store: Store): String {
        return store.joinToString("\n") { "${it.s.encoded()} ${it.p.encoded()} ${it.o.encoded()} ." }
    }

    fun decodeFromNTriples(content: Sequence<String>): Store {
        val result = Store()
        content.forEach { result.add(it.decodeSingleLine()) }
        return result
    }

    fun String.decodeSingleLine(): Quad {
        var i = 0
        val subj: Quad.Term
        val pred: Quad.NamedTerm
        val obj: Quad.Term
        decodeNextTerm(start = i).let {
            subj = it.first
            i = it.second
        }
        decodeNextTerm(start = i).let {
            pred = it.first as? Quad.NamedTerm ?: throw IllegalArgumentException("Term `${it.first}` is not a valid predicate!")
            i = it.second
        }
        decodeNextTerm(start = i).let {
            obj = it.first
        }
        return Quad(s = subj, p = pred, o = obj)
    }

    private fun String.decodeNextTerm(start: Int): Pair<Quad.Term, Int> {
        var i = start
        while (i < length && this[i].isWhitespace()) {
            ++i
        }
        val c = this[i]
        when {
            c == '<' -> {

            }
            c == '\'' -> {

            }
            c == '"' -> {

            }
            c.isDigit() -> {

            }
        }
    }

    private fun String.decodeNextUri(start: Int): Pair<Quad.Term, Int> {

    }

    private fun String.decodeNextNumericLiteral(start: Int): Pair<Quad.Literal<*>, Int> {

    }

    private fun String.decodeNextStringLiteral(start: Int): Pair<Quad.Literal<String>, Int> {

    }

    private fun Quad.Term.encoded(): String {
        return when (this) {
            is Quad.BlankTerm -> "_:b$id"
            is Quad.Literal<*> -> toString()
            is Quad.NamedTerm -> "<$value>"
        }
    }

}
