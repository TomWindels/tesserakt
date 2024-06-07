package dev.tesserakt.sparql.runtime.incremental.types

import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.node.IncrementalNode
import kotlin.jvm.JvmInline

@JvmInline
value class Patterns(private val items: List<Pattern>): List<Pattern> by items, IncrementalNode

@JvmInline
value class Union(val segments: List<Segment>): List<Segment> by segments, IncrementalNode

@JvmInline
value class Optional(val segment: Segment): IncrementalNode

sealed interface Segment

@JvmInline
value class StatementsSegment(val statements: Query.QueryBody): Segment, IncrementalNode

@JvmInline
value class SelectQuerySegment(val query: SelectQuery): Segment, IncrementalNode

