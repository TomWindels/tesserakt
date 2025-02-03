package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.types.DebugWriter
import dev.tesserakt.sparql.runtime.incremental.types.Query
import dev.tesserakt.sparql.runtime.util.Bitmask
import dev.tesserakt.sparql.runtime.util.getAllNamedBindings

internal class IncrementalBasicGraphPatternState(ast: Query.QueryBody) {

    private val patterns = IncrementalPatternsState(ast.patterns)
    private val unions = JoinTree(ast.unions)
    private val optionals = JoinTree(ast, ast.optional)

    /**
     * A collection of all bindings found inside this query body; it is not guaranteed that all solutions generated
     *  through [insert]ion have a value for all of these bindings, as this depends on the query itself
     */
    val bindings: Set<String> = ast.getAllNamedBindings().map { it.name }.toSet()

    fun insert(delta: Delta.Data): List<Delta.Bindings> {
        val total = peek(delta)
        process(delta)
        return total
    }

    fun peek(delta: Delta.Data): List<Delta.Bindings> {
        // applying peek logic similar to the None join tree type, where overlapping compatibilities are also expanded
        //  upon before joined with prior data found in the remaining sections of the query body
        val matches = listOf(
            Bitmask.onesAt(0, length = 2) to patterns.peek(delta),
            Bitmask.onesAt(1, length = 2) to unions.peek(delta),
        )
            .expandBindingDeltas()
            .flatMap { (completed, delta) ->
                var result = delta
                // 0 = patterns in the list
                if (0 !in completed) {
                    result = patterns.join(result)
                }
                // 1 = unions in the list
                if (1 !in completed) {
                    result = unions.join(result)
                }
                result
            }
        // temporarily adding it to the optionals
        val three = optionals.peek(delta)
        optionals.process(delta)
        // and joining it as such
        val results = optionals.join(matches)
        // tossing it out again
        val inverse = when (delta) {
            is Delta.DataAddition -> Delta.DataDeletion(value = delta.value)
            is Delta.DataDeletion -> Delta.DataAddition(value = delta.value)
        }
        optionals.process(inverse)
        return results + unions.join(patterns.join(three))
    }

    fun process(delta: Delta.Data) {
        patterns.process(delta)
        unions.process(delta)
        optionals.process(delta)
    }

    fun join(delta: Delta.Bindings): List<Delta.Bindings> {
        return optionals.join(unions.join(patterns.join(delta)))
    }

    fun debugInformation(writer: DebugWriter) {
        writer.block(" * ") {
            appendLine("Pattern state")
            patterns.debugInformation(writer)
        }
        writer.block(" * ") {
            appendLine("Optional state")
            optionals.debugInformation(writer)
        }
        writer.block(" * ") {
            appendLine("Union state")
            unions.debugInformation(writer)
        }
    }

}
