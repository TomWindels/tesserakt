package dev.tesserakt.rdf.dsl

import dev.tesserakt.rdf.dsl.RDF.Environment
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

typealias RDF_DSL = RDF.() -> Unit

@OptIn(ExperimentalContracts::class)
fun RDF_DSL.extractPrefixes(): Map<String, String> {
    contract {
        callsInPlace(this@extractPrefixes, InvocationKind.EXACTLY_ONCE)
    }
    return RDF(
        environment = Environment(""),
        consumer = object: RDF.Consumer {
            override fun process(
                subject: Quad.NamedTerm,
                predicate: Quad.NamedTerm,
                `object`: Quad.Term,
                graph: Quad.Graph
            ) {
                // nop
            }

            override fun process(
                subject: Quad.BlankTerm,
                predicate: Quad.NamedTerm,
                `object`: Quad.Term,
                graph: Quad.Graph
            ) {
                // nop
            }
        }
    )
        .apply(this)
        .prefixes
}

@OptIn(ExperimentalContracts::class)
fun buildStore(path: String = "", block: RDF.() -> Unit): Store {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return MutableStore().apply { insert(Environment(path = path), block) }
}

@OptIn(ExperimentalContracts::class)
fun buildStore(environment: Environment, block: RDF.() -> Unit): Store {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return MutableStore().apply { insert(environment, block) }
}

@OptIn(ExperimentalContracts::class)
fun MutableStore.insert(environment: Environment = Environment(path = ""), block: RDF.() -> Unit): Store {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    RDF(
        environment = environment,
        consumer = StoreAdapter(this)
    )
        .apply(block)
    return this
}
