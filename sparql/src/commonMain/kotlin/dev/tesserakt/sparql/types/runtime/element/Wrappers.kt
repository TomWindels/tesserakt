package dev.tesserakt.sparql.types.runtime.element

import kotlin.jvm.JvmInline

@JvmInline
value class Patterns(private val items: List<Pattern>): List<Pattern> by items, RuntimeElement

@JvmInline
value class Union(val segments: List<Segment>): List<Segment> by segments, RuntimeElement

@JvmInline
value class Optional(val segment: Segment): RuntimeElement

sealed interface Segment : RuntimeElement

@JvmInline
value class StatementsSegment(val statements: Query.QueryBody): Segment, RuntimeElement

@JvmInline
value class SelectQuerySegment(val query: SelectQuery): Segment, RuntimeElement

data class BindingStatement(
    val expression: Expression,
    val target: String
)
