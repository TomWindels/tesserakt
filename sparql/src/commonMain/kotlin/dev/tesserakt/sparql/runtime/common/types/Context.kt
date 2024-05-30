package dev.tesserakt.sparql.runtime.common.types

/**
 * Generic context-like object, representing either a single binding solution propagating through, or an aggregate
 *  of bindings in queries utilizing `GROUP BY`
 */
sealed interface Context {

    /**
     * Context object representing a single binding solution propagating through
     */
    data class Singular(
        /** The currently relevant bindings when iterating through individual solutions **/
        val current: Bindings,
        /** All bindings (including `current`) generated, guaranteed to be complete w.r.t. available data **/
        val all: List<Bindings>
    ): Context

    /**
     * Context object representing a collection of binding solutions for a single group currently propagating through
     */
    data class Aggregate(
        /** The currently relevant bindings when iterating through individual solutions **/
        val current: List<Bindings>,
        /** All binding groups (including `current`) generated, guaranteed to be complete w.r.t. available data **/
        val all: List<List<Bindings>>
    ): Context

}
