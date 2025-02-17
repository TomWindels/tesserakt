package dev.tesserakt.rdf.trig.serialization

import dev.tesserakt.rdf.serialization.common.Prefixes
import dev.tesserakt.rdf.types.Quad

object TriGSerializer {

    val NoPrefixes = Prefixes(emptyMap())

    fun serialize(
        store: Collection<Quad>,
        prefixes: Prefixes = NoPrefixes,
    ): String {
        val formatter = PrettyFormatter(prefixes)
        val serializer = Tokenizer(store)
        return serializer.iterator().writeToString(formatter)
    }

    fun serialize(
        store: Collection<Quad>,
        prefixes: Map<String, String>,
    ): String {
        val formatter = PrettyFormatter(Prefixes(prefixes))
        val serializer = Tokenizer(store)
        return serializer.iterator().writeToString(formatter)
    }

    inline fun serialize(
        store: Collection<Quad>,
        prefixes: Map<String, String>,
        callback: (String) -> Unit
    ) {
        serialize(store, PrettyFormatter(Prefixes(prefixes))).forEach(callback)
    }

    fun serialize(
        store: Collection<Quad>,
        formatter: Formatter,
    ): Iterator<String> {
        return formatter.format(Tokenizer(store).iterator())
    }

    private class Tokenizer(store: Collection<Quad>) : Iterable<TriGToken> {

        private val data = store.optimise()

        override fun iterator(): Iterator<TriGToken> = iterator {
            data.forEach { yieldGraph(it) }
        }

        private suspend fun SequenceScope<TriGToken>.yieldGraph(entry: Map.Entry<Quad.Graph, Map<Quad.Term, Map<Quad.NamedTerm, List<Quad.Term>>>>) {
            wrap(entry.key) {
                entry.value.forEach { yieldGraphEntry(it) }
            }
        }

        private suspend fun SequenceScope<TriGToken>.yieldGraphEntry(entry: Map.Entry<Quad.Term, Map<Quad.NamedTerm, List<Quad.Term>>>) {
            yield(entry.key.toToken())
            val values = entry.value.toList()
            repeat(values.size - 1) {
                yieldPredicate(values[it])
                yield(TriGToken.Structural.PredicateTermination)
            }
            yieldPredicate(values.last())
            yield(TriGToken.Structural.StatementTermination)
        }

        private suspend fun SequenceScope<TriGToken>.yieldPredicate(entry: Pair<Quad.Term, List<Quad.Term>>) {
            yield(entry.first.toToken())
            repeat(entry.second.size - 1) {
                yield(entry.second[it].toToken())
                yield(TriGToken.Structural.ObjectTermination)
            }
            yield(entry.second.last().toToken())
        }

        private suspend inline fun SequenceScope<TriGToken>.wrap(graph: Quad.Graph, block: () -> Unit) {
            when (graph) {
                Quad.DefaultGraph -> {
                    block()
                }
                is Quad.BlankTerm -> {
                    yield(graph.toToken())
                    yield(TriGToken.Structural.GraphStatementStart)
                    block()
                    yield(TriGToken.Structural.GraphStatementEnd)
                }
                is Quad.NamedTerm -> {
                    yield(graph.toToken())
                    yield(TriGToken.Structural.GraphStatementStart)
                    block()
                    yield(TriGToken.Structural.GraphStatementEnd)
                }
            }
        }

        private fun Quad.Term.toToken(): TriGToken = when (this) {
            is Quad.BlankTerm ->
                TriGToken.PrefixedTerm(prefix = "_", value = "b$id")

            is Quad.Literal ->
                TriGToken.LiteralTerm(value = value, type = type.toToken() as TriGToken.NonLiteralTerm)

            is Quad.NamedTerm ->
                TriGToken.Term(value = value)
        }

    }

    private fun Iterator<TriGToken>.writeToString(formatter: Formatter): String {
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
    private fun Collection<Quad>.optimise() = this
        .groupBy { quad -> quad.g }
        .mapValues { entry ->
            entry.value
                .groupBy { quad -> quad.s }
                .mapValues { entry ->
                    entry.value.groupBy(
                        keySelector = { quad -> quad.p },
                        valueTransform = { quad -> quad.o }
                    )
                }
        }

    private fun <T> iteratorOf(element: T): Iterator<T> = listOf(element).iterator()

}
