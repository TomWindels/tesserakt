package dev.tesserakt.sparql.runtime.incremental.types

import kotlin.jvm.JvmInline

@JvmInline
internal value class Cardinality(private val value: Double): Comparable<Cardinality> {

    constructor(value: Number): this(value = value.toDouble())

    init {
        require(value >= 0)
    }

    operator fun times(other: Cardinality): Cardinality {
        return Cardinality(value = this.value.times(other.value))
    }

    operator fun times(other: Number): Cardinality {
        return Cardinality(value = this.value.times(other.toDouble()))
    }

    operator fun plus(other: Cardinality): Cardinality {
        return Cardinality(value = this.value.plus(other.value))
    }

    operator fun plus(other: Number): Cardinality {
        return Cardinality(value = this.value.plus(other.toDouble()))
    }

    override fun compareTo(other: Cardinality): Int {
        return value.compareTo(other.value)
    }

    fun toInt() = value.toInt()

    fun toDouble() = value

}
