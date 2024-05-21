package dev.tesserakt.sparql.compiler.types

import dev.tesserakt.sparql.compiler.extractAllBindings
import kotlin.jvm.JvmInline

data class SelectQueryAST(
    val output: Output,
    override val body: QueryBodyAST
): QueryAST() {

    @JvmInline
    value class Output internal constructor(val entries: Map<String, Entry>): AST {

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
        value class BindingEntry(val binding: PatternAST.Binding): Entry {
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

        fun addToOutput(binding: PatternAST.Binding) {
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
                body.extractAllBindings().map { Output.BindingEntry(it) }
            } else {
                entries
            }
            return SelectQueryAST(
                output = Output(outputs),
                body = body
            )
        }

    }

}
