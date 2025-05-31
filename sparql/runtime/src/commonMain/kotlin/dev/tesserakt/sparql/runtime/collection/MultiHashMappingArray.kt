package dev.tesserakt.sparql.runtime.collection

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.ZeroCardinality
import kotlin.jvm.JvmInline

/**
 * An array useful for storing a series of mappings, capable of joining with other mappings using the hash join
 *  algorithm. Hash tables are created for every binding name passed in the constructor.
 */
class MultiHashMappingArray(
    // the binding set associated with the index
    private val indexBindingSet: BindingIdentifierSet
): MappingArray {

    sealed interface Node {

        fun iter(): OptimisedStream<Mapping>

        fun iter(reference: Mapping): OptimisedStream<Mapping>

        fun add(value: Mapping)

        fun remove(value: Mapping)

        class Intermediate private constructor(
            private val binding: BindingIdentifier,
            private val factory: () -> Node,
            private val inner: MutableMap<Int /* -1 for not found */, Node>,
        ) : Node {

            constructor(
                binding: BindingIdentifier,
                factory: () -> Node,
            ): this(
                binding = binding,
                factory = factory,
                inner = mutableMapOf()
            )

            var cardinality: Cardinality = ZeroCardinality
                private set

            override fun iter(): OptimisedStream<Mapping> {
                return if (inner.values.isEmpty()) {
                    emptyStream()
                } else {
                    OptimisedStreamView(inner.values.toStream().transform(cardinality.value / inner.values.size) { it.iter() })
                }
            }

            override fun iter(reference: Mapping): OptimisedStream<Mapping> {
                val term = reference.get(binding)
                return if (term == null) {
                    // the reference has no constraints, so all of our inner values are returned
                    if (inner.values.isEmpty()) {
                        emptyStream()
                    } else {
                        OptimisedStreamView(inner.values.toStream().transform(cardinality.value / inner.values.size) { it.iter(reference) })
                    }
                } else {
                    // if the hash value is known, we can return it directly; otherwise, we can guarantee it doesn't
                    //  exist
                    inner[term.id]?.iter(reference) ?: emptyStream()
                }
            }

            override fun add(value: Mapping) {
                inner.getOrPut(value.get(binding)?.id ?: -1) { factory() }
                    .add(value)
                cardinality += 1
            }

            override fun remove(value: Mapping) {
                val loc = inner[value.get(binding)?.id ?: -1]
                    ?: throw IllegalStateException("Could not remove $value as its expected index location does not exist!")
                loc.remove(value)
                cardinality -= 1
            }

        }

        @JvmInline
        value class Terminal private constructor(private val elements: SimpleMappingArray) : Node {

            constructor(): this(elements = SimpleMappingArray())

            override fun iter(): OptimisedStream<Mapping> {
                return elements.iter()
            }

            override fun iter(reference: Mapping): OptimisedStream<Mapping> {
                return elements.iter()
            }

            override fun add(value: Mapping) {
                elements.add(value)
            }

            override fun remove(value: Mapping) {
                elements.remove(value)
            }

        }

        companion object {

            fun create(indexes: BindingIdentifierSet): Node {
                val iter = indexes.asIterable()
                var factory: () -> Node = ::Terminal
                iter.forEach { binding ->
                    // making a copy of the current factory reference, which is then inserted in the new factory
                    val inner = factory
                    factory = { Intermediate(binding, inner) }
                }
                return factory()
            }

        }

    }

    constructor(
        context: QueryContext,
        bindings: Set<String>
    ): this(indexBindingSet = BindingIdentifierSet(context, bindings))

    private val backing = Node.create(indexBindingSet)
    private val cache = mutableMapOf<Mapping?, OptimisedStream<Mapping>>()

    init {
        check(indexBindingSet.size > 0) { "Invalid use of MultiHashMappingArray! No bindings are used!" }
    }

    override var cardinality = Cardinality(0)
        private set

    override fun iter(mapping: Mapping): OptimisedStream<Mapping> {
        val relevant = mapping.retain(indexBindingSet)
        return cache.getOrPut(relevant.takeIf { it.count != 0 }) {
            if (relevant.count == 0) {
                backing.iter()
            } else {
                backing.iter(relevant)
            }
        }
    }

    override fun iter(mappings: List<Mapping>): List<OptimisedStream<Mapping>> {
        return mappings.map { iter(it) }
    }

    override fun iter(): OptimisedStream<Mapping> {
        return cache.getOrPut(null) { backing.iter() }
    }

    /**
     * Adds a mapping to the backing array and indexes it accordingly.
     */
    override fun add(mapping: Mapping) {
        backing.add(mapping)
        cardinality += 1
        cache.clear()
    }

    /**
     * Adds all mappings to the backing array and indexes it accordingly.
     */
    override fun addAll(mappings: Iterable<Mapping>) {
        mappings.forEach { add(it )}
    }

    override fun remove(mapping: Mapping) {
        backing.remove(mapping)
        cardinality -= 1
        cache.clear()
    }

    override fun removeAll(mappings: Iterable<Mapping>) {
        mappings.forEach { remove(it )}
    }

    override fun toString(): String =
        "MultiHashMappingArray (cardinality $cardinality, indexed on ${indexBindingSet})"

}
