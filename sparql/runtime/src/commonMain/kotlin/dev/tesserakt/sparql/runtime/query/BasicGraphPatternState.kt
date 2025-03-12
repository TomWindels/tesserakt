package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingAddition
import dev.tesserakt.sparql.runtime.evaluation.MappingDeletion
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.Filter
import dev.tesserakt.sparql.types.GraphPattern
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.getAllNamedBindings

class BasicGraphPatternState(ast: GraphPattern) {

    private val patterns = JoinTree(ast.patterns)
    private val unions = JoinTree(ast.unions)

    // all inner bindings, used in when deducing what bindings to exclude through filters
    private val inner = patterns.bindings + unions.bindings

    private val excluded = ast.filters
        .filterIsInstance<Filter.NotExists>()
        .map { BasicGraphPatternState(it.pattern) }

    /**
     * A collection of all bindings found inside this query body; it is not guaranteed that all solutions generated
     *  through [insert]ion have a value for all of these bindings, as this depends on the query itself
     */
    val bindings: Set<String> = ast.getAllNamedBindings().map { it.name }.toSet()

    val cardinality: Cardinality
        get() = patterns.cardinality * unions.cardinality

    fun insert(delta: DataDelta): List<MappingDelta> {
        // it's important we collect the results before we process the delta
        val total = peek(delta).collect()
        process(delta)
        return total
    }

    fun peek(delta: DataDelta): Stream<MappingDelta> {
        val first = patterns.peek(delta)
        val second = unions.peek(delta)
        // combining these states to get a total set of potential resulting mappings
        val max = patterns.join(second).chain(unions.join(first))
        // of these mappings, only the ones with no match in the excluded graph patterns can continue...
        val filtered = max
            .filtered { mapping ->
                // the mapping should not be able to join with any of the excluded graph patterns
                excluded.none { excludedGraphPattern ->
                    // should not be capable of joining with prior data
                    excludedGraphPattern.join(mapping).iterator().hasNext() ||
                    // nor with this new data added
                    mapping.value.join(
                        excludedGraphPattern.peek(delta).mappedNonNull { if (it is MappingAddition) it.value else null }
                    ).iterator().hasNext()
                }
            }
        // ...whilst additional "old" ones have to be removed
        val two = excluded.toStream().transform(patterns.cardinality * unions.cardinality) { excludedGraphPattern ->
            val peeked = excludedGraphPattern.peek(delta).optimisedForReuse()
            val additions = peeked.filteredIsInstance<MappingAddition>()
            // transforming new mappings into the subset visible to the main patterns, only keeping those
            //  smaller bindings that are actually new, and letting them join on past data to get all affected
            //  results
            // 1: getting a relevant subset of guaranteed excluded mappings
            val subset = additions.mapped { it.value.retain(inner) }.toSet()
            // 2: getting its subset that is actually *new* compared to previous iterations
            val new = subset
                .mapNotNull {
                    // if the subset can join with this graph pattern's past data, it's not new
                    val isNewMapping = !excludedGraphPattern
                        .join(MappingAddition(it, null))
                        .iterator()
                        .hasNext()
                    if (isNewMapping) MappingDeletion(it, null) else null
                }
                .toStream()
            // 3: having the new stream affect the original (past) data
            val nowExcluded = new.transform(patterns.cardinality * unions.cardinality) {
                unions.join(patterns.join(it).optimisedForSingleUse())
            }
            // doing the same in the opposite direction: binding groups now excluded should affect prior results as well
            val deletions = peeked.filteredIsInstance<MappingDeletion>()
            val subset2 = deletions.mapped { it.value.retain(inner) }.toSet()
            val new2 = subset2
                .mapNotNull {
                    // if the subset can join with this graph pattern's past data, it's not new
                    val isNewMapping = !excludedGraphPattern
                        .join(MappingAddition(it, null))
                        .iterator()
                        .hasNext()
                    if (isNewMapping) MappingAddition(it, null) else null
                }
                .toStream()
            val nowIncluded = new2.transform(patterns.cardinality * unions.cardinality) {
                unions.join(patterns.join(it).optimisedForSingleUse())
            }
            nowExcluded.chain(nowIncluded)
        }
        return filtered.chain(two)
    }

    fun process(delta: DataDelta) {
        patterns.process(delta)
        unions.process(delta)
        excluded.forEach { it.process(delta) }
    }

    fun join(delta: MappingDelta): Stream<MappingDelta> {
        return unions.join(patterns.join(delta).optimisedForSingleUse())
    }

    fun debugInformation() = buildString {
        appendLine("* Pattern state")
        append(patterns.debugInformation())
    }

}
