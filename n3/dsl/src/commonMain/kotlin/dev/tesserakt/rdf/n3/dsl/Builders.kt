package dev.tesserakt.rdf.n3.dsl

import dev.tesserakt.rdf.n3.ExperimentalN3Api
import dev.tesserakt.rdf.n3.Store
import dev.tesserakt.rdf.n3.dsl.N3Context.Environment
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


@OptIn(ExperimentalContracts::class, ExperimentalN3Api::class)
fun N3(path: String = "", block: N3Context.() -> Unit): Store {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return Store().apply { insert(Environment(path = path), block) }
}

@OptIn(ExperimentalContracts::class, ExperimentalN3Api::class)
fun N3(environment: Environment, block: N3Context.() -> Unit): Store {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return Store().apply { insert(environment, block) }
}

@OptIn(ExperimentalContracts::class, ExperimentalN3Api::class)
fun Store.insert(environment: Environment, block: N3Context.() -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    N3Context(
        environment = environment,
        consumer = StoreAdapter(this)
    )
        .apply(block)
}
