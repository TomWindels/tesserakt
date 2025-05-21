package dev.tesserakt.sparql.util

import kotlin.jvm.JvmInline
import kotlin.math.roundToLong

@JvmInline
value class Cardinality(internal val value: Double): Comparable<Cardinality> {

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

    operator fun minus(other: Cardinality): Cardinality {
        return Cardinality(value = this.value.minus(other.value))
    }

    operator fun minus(other: Number): Cardinality {
        return Cardinality(value = this.value.minus(other.toDouble()))
    }

    override fun compareTo(other: Cardinality): Int {
        return value.compareTo(other.value)
    }

    fun toInt() = value.toInt()

    fun toDouble() = value

    override fun toString(): String {
        return value.roundToLong().toString()
    }

}
