package dev.tesserakt.rdf.serialization

import dev.tesserakt.rdf.n3.ExperimentalN3Api
import dev.tesserakt.rdf.n3.Quad
import dev.tesserakt.rdf.n3.Store

@ExperimentalN3Api
object N3Serializer {

    val DefaultFormatter = PrettyFormatter()

    fun serialize(store: Store, formatter: Formatter = DefaultFormatter): String {
        val serializer = Tokenizer(store)
        return serializer.iterator().writeToString(formatter)
    }

    private class Tokenizer(store: Store) : Iterable<N3Token> {

        private val data = store.optimise()

        override fun iterator(): Iterator<N3Token> = iterator {
            data.forEach { yieldEntry(it) }
        }

        private suspend fun SequenceScope<N3Token>.yieldEntry(entry: Map.Entry<Quad.Term, Map<Quad.Term, List<Quad.Term>>>) {
            yieldAll(entry.key.tokenized())
            val values = entry.value.toList()
            repeat(values.size - 1) {
                yieldPredicate(values[it])
                yield(N3Token.Structural.PredicateTermination)
            }
            yieldPredicate(values.last())
            yield(N3Token.Structural.StatementTermination)
        }

        private suspend fun SequenceScope<N3Token>.yieldPredicate(entry: Pair<Quad.Term, List<Quad.Term>>) {
            yieldAll(entry.first.tokenized())
            repeat(entry.second.size - 1) {
                yieldAll(entry.second[it].tokenized())
                yield(N3Token.Structural.ObjectTermination)
            }
            yieldAll(entry.second.last().tokenized())
        }

        private fun Quad.Term.tokenized(): Iterator<N3Token> = when (this) {
            is Quad.Term.RdfTerm ->
                iteratorOf(term.tokenized())

            is Quad.Term.StatementsList ->
                iterator {
                    yield(N3Token.Structural.StatementsListStart)
                    statements.optimise().forEach { yieldEntry(it) }
                    yield(N3Token.Structural.StatementsListEnd)
                }
        }

        private fun dev.tesserakt.rdf.types.Quad.Term.tokenized(): N3Token = when (this) {
            is dev.tesserakt.rdf.types.Quad.BlankTerm ->
                N3Token.PrefixedTerm(prefix = "_", value = "b$id")

            is dev.tesserakt.rdf.types.Quad.Literal<*> ->
                N3Token.LiteralTerm(value = value, type = type.tokenized() as N3Token.NonLiteralTerm)

            is dev.tesserakt.rdf.types.Quad.NamedTerm ->
                N3Token.Term(value = value)
        }

    }

    private fun Iterator<N3Token>.writeToString(formatter: Formatter): String {
        val result = StringBuilder()
        formatter
            .format(this)
            .forEach { text -> result.append(text) }
        return result.toString()
    }

    /**
     * Sorts the store by its subjects first, followed by its predicates, to allow for a more compact
     *  string representation
     */
    private fun Store.optimise() = this
        .groupBy { quad -> quad.s }
        .mapValues { entry ->
            entry.value.groupBy(
                keySelector = { quad -> quad.p },
                valueTransform = { quad -> quad.o })
        }

    private fun <T> iteratorOf(element: T): Iterator<T> = listOf(element).iterator()

}
