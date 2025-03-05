package dev.tesserakt.sparql.runtime.core

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Bindings

private val keys = js("Object.keys")
private val assign = js("Object.assign")

private fun getKeys(obj: dynamic) = keys(obj).unsafeCast<Array<String>>()

internal actual class Mapping private constructor(value: Any): Iterable<Map.Entry<String, Quad.Term>> {

    private val value: dynamic = value

    actual constructor(value: Map<String, Quad.Term>): this(
        value = kotlin.run {
            val result: dynamic = Any()
            value.forEach {
                result[it.key] = it.value
            }
            result as Any
        }
    )

    actual constructor(value: List<Map.Entry<String, Quad.Term>>): this(
        value = kotlin.run {
            val result: dynamic = Any()
            value.forEach {
                result[it.key] = it.value
            }
            result as Any
        }
    )

    actual val keys: Set<String> = getKeys(value).toSet()

    actual val bindings: Bindings = keys.associateWith { this.value[it] }

    actual fun compatibleWith(other: Mapping): Boolean {
        val common = keys.intersect(other.keys)
        return common.all { key -> this[key] == other[key] }
    }

    actual operator fun plus(other: Mapping): Mapping {
        val merged = Any()
        assign(merged, value, other.value)
        return Mapping(merged)
    }

    actual operator fun get(name: String): Quad.Term? {
        return value[name] as Quad.Term?
    }

    override fun iterator(): Iterator<Map.Entry<String, Quad.Term>> {
        return bindings.iterator()
    }

    private val hashCode = bindings.hashCode()

    override fun hashCode(): Int {
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Mapping) {
            return false
        }
        return hashCode == other.hashCode() && bindings == other.bindings
    }

    override fun toString(): String {
        return JSON.stringify(value)
    }

}
