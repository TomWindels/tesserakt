package dev.tesserakt.sparql.types

import kotlin.jvm.JvmInline

@JvmInline
value class TriplePatternSet(private val items: List<TriplePattern>): List<TriplePattern> by items, QueryAtom

@JvmInline
value class Union(val segments: List<Segment>): List<Segment> by segments, QueryAtom

@JvmInline
value class Optional(val segment: Segment): QueryAtom

sealed interface Segment : QueryAtom

@JvmInline
value class GraphPatternSegment(val pattern: GraphPattern): Segment, QueryAtom

@JvmInline
value class SelectQuerySegment(val query: SelectQueryStructure): Segment, QueryAtom

data class BindingStatement(
    val expression: Expression,
    val target: Binding
): QueryAtom
