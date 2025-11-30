package dev.tesserakt.rdf.serialization.turtle

import dev.tesserakt.rdf.serialization.common.Prefixes


class TurtleConfig(
    var base: String = DEFAULT_BASE,
    var formatter: TurtleFormatter = DEFAULT_TURTLE_FORMATTER
) {

    data class PrettyFormatterConf(
        internal var prefixes: Prefixes = NoPrefixes,
        /**
         * The (group of) character(s) to repeat for every depth in the resulting structure, typically either
         *  a set of spaces or tabs
         */
        internal var indent: PrettyTurtleFormatter.Indent = PrettyTurtleFormatter.FixedStepIndent(INDENT_PATTERN),
    ) {
        fun build() = PrettyTurtleFormatter(
            prefixes = prefixes,
            indent = indent,
        )
    }

    companion object {
        val NoPrefixes = Prefixes(emptyMap())
    }
}
