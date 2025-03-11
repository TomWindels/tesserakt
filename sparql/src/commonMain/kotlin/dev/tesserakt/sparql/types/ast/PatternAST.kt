package dev.tesserakt.sparql.types.ast

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.compiler.lexer.Token
import kotlin.jvm.JvmInline

data class PatternAST(
    val s: Subject,
    val p: Predicate,
    val o: Object
): ASTElement {
    // collection of all possible subject pattern element types
    sealed interface Subject: ASTElement
    // collection of all possible predicate pattern element types
    sealed interface Predicate: ASTElement
    // collection of all possible object pattern element types
    sealed interface Object: ASTElement
    // collection of types possible at any spot (s, p & o)
    sealed interface Element: Subject, Predicate, Object

    /** reused binding (either within the corresponding pattern, or inside the (sub)query's exports **/
    @JvmInline
    value class Binding(val name: String): Element {
        constructor(token: Token.Binding): this(name = token.name)
    }
    /** "exact" value, e.g. `<predicate>` **/
    @JvmInline
    value class Exact(val term: Quad.Term): Element

    /** simple blank object only constrained by a series of properties (sub-patterns) **/
    @JvmInline
    value class BlankObject(val properties: List<BlankPattern>): Object {
        data class BlankPattern(val p: Predicate, val o: Object)
    }

    /** inverse of "exact" value, e.g. `(!<predicate>)` **/
    @JvmInline
    value class Not(val predicate: Predicate): Predicate
    /** set of allowed types only, e.g. `(<predicate1>|<predicate2>)` **/
    // would this ever have to be expanded to support subject & objects?
    @JvmInline
    value class Alts(val allowed: List<Predicate>): Predicate {
        constructor(vararg predicates: Predicate): this(predicates.flatMap { if (it is Alts) it.allowed else listOf(it) })
    }
    /** multiple predicates chained together, e.g. <predicate1>/<predicate2> **/
    @JvmInline
    value class Chain(val chain: List<Predicate>): Predicate {
        constructor(vararg predicates: Predicate): this(predicates.flatMap { if (it is Chain) it.chain else listOf(it) })
    }
    /** regular predicate being repeated, zero or more relationship(s) present (`<predicate>+`) **/
    @JvmInline
    value class ZeroOrMore(val value: Predicate): Predicate
    /** regular predicate being repeated, at least one relationship present (`<predicate>+`) **/
    @JvmInline
    value class OneOrMore(val value: Predicate): Predicate

}
