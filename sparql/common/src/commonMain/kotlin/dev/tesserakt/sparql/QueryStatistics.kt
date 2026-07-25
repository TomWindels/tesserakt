package dev.tesserakt.sparql

/**
 * Provides a 'DESCRIBE'-like object that can be used to track ongoing query statistics.
 */
interface QueryStatistics {

    enum class Granularity {
        /**
         * Only retain the logical structure of the query evaluation operators, with no descriptions.
         */
        STRUCTURE_ONLY,

        /**
         * Provide a high level set of query structure descriptions
         */
        HIGH_LEVEL,

        /**
         * Provide more detailed query structure definitions
         */
        DETAILED,

        /**
         * Provide all possible information with the query structure
         */
        VERBOSE,
        ;

        infix fun isAtLeast(threshold: Granularity) : Boolean {
            return ordinal >= threshold.ordinal
        }

    }

    /**
     * Creates a new instance of this element compared to the [reference] element (difference in change count).
     * This method fails if the structure mismatches with the [reference], yielding an [IllegalArgumentException].
     *
     * This is mainly intended for scenarios where the impact of data changes is to be observed w.r.t. the query
     *  structure.
     */
    fun diff(reference: QueryStatistics): QueryStatistics

}
