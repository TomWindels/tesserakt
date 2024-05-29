package dev.tesserakt.sparql.compiler.ast

import dev.tesserakt.sparql.compiler.extractAllBindings
import dev.tesserakt.sparql.formatting.ASTWriter
import kotlin.jvm.JvmInline

data class SelectQueryAST(
    // TODO "distinct" ?
    val output: Map<String, OutputEntry>,
    override val body: QueryBodyAST,
    /** GROUP BY <expr> **/
    val grouping: Expression?,
    /** HAVING (filter) **/
    val groupingFilter: Expression.Filter?,
    /** ORDER BY <expr> **/
    val ordering: Expression?
): QueryAST() {

    sealed interface OutputEntry: ASTNode {
        val name: String
    }

    @JvmInline
    value class BindingOutputEntry(val binding: PatternAST.Binding): OutputEntry {
        override val name: String get() = binding.name
    }

    @JvmInline
    value class AggregationOutputEntry(val aggregation: Aggregation): OutputEntry {
        override val name: String get() = aggregation.target.name
    }

    override fun toString(): String {
        // even though base class overwrites it, `data class` re-overwrites it
        return ASTWriter().write(this)
    }

    class Builder {

        private var everything = false
        private val entries = mutableListOf<OutputEntry>()
        lateinit var body: QueryBodyAST
        // GROUP BY <expr>
        var grouping: Expression? = null
        var groupingFilter: Expression.Filter? = null
        // ORDER BY <expr>
        var ordering: Expression? = null

        fun addToOutput(binding: PatternAST.Binding) {
            entries.add(BindingOutputEntry(binding))
        }

        fun addToOutput(aggregation: Aggregation) {
            entries.add(AggregationOutputEntry(aggregation))
        }

        fun setEverythingAsOutput() {
            everything = true
        }

        fun build(): SelectQueryAST {
            val outputs = if (everything) {
                body.extractAllBindings().map { BindingOutputEntry(it) }
            } else {
                entries
            }
            return SelectQueryAST(
                output = outputs.associateBy { it.name },
                body = body,
                grouping = grouping,
                groupingFilter = groupingFilter,
                ordering = ordering
            )
        }

    }

}
