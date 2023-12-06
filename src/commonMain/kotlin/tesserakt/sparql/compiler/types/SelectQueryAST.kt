package tesserakt.sparql.compiler.types

import tesserakt.sparql.compiler.bindings
import kotlin.jvm.JvmInline

data class SelectQueryAST(
    val output: Output,
    override val body: QueryBodyAST
): QueryAST() {

    @JvmInline
    value class Output internal constructor(val entries: Map<String, Entry>) {

        val names get() = entries.keys

        // TODO "distinct" ?

        internal constructor(
            entries: Iterable<Entry>
        ): this(
            entries = entries.associateBy { it.name }
        )

        fun binding(name: String) = (entries[name] as? BindingEntry)?.binding

        fun aggregate(name: String) = (entries[name] as? AggregationEntry)?.aggregation

        sealed interface Entry {
            val name: String
        }

        @JvmInline
        value class BindingEntry(val binding: Pattern.Binding): Entry {
            override val name: String get() = binding.name
        }

        @JvmInline
        value class AggregationEntry(val aggregation: Aggregation): Entry {
            override val name: String get() = aggregation.output.name
        }

    }

    class Builder {

        private var everything = false
        private val entries = mutableListOf<Output.Entry>()
        lateinit var body: QueryBodyAST

        fun addToOutput(binding: Pattern.Binding) {
            entries.add(Output.BindingEntry(binding))
        }

        fun addToOutput(aggregation: Aggregation) {
            entries.add(Output.AggregationEntry(aggregation))
        }

        fun setEverythingAsOutput() {
            everything = true
        }

        fun build(): SelectQueryAST {
            val outputs = if (everything) {
                (
                    body.patterns
                        .flatMap { pattern -> pattern.bindings() } +
                    body.unions
                        .flatMap { union -> union.flatMap { block -> block.flatMap { pattern -> pattern.bindings() } } } +
                    body.optional
                        .flatMap { optional -> optional.flatMap { pattern -> pattern.bindings() } }
                ).distinct().map { Output.BindingEntry(it) }
            } else {
                entries
            }
            return SelectQueryAST(
                output = Output(outputs),
                body = body
            )
        }

    }

    override val subqueries: List<QueryAST>
        // FIXME when subqueries are supported
        get() = emptyList()

}