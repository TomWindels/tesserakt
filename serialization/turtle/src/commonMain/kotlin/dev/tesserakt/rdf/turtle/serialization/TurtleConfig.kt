package dev.tesserakt.rdf.turtle.serialization

import dev.tesserakt.rdf.serialization.common.Prefixes
import dev.tesserakt.rdf.turtle.serialization.PrettyFormatter.FixedStepIndent
import dev.tesserakt.rdf.turtle.serialization.PrettyFormatter.Indent


class TurtleConfig(
    var formatter: Formatter = DEFAULT_FORMATTER
) {

    data class PrettyFormatterConf(
        internal var prefixes: Prefixes = NoPrefixes,
        /**
         * The (group of) character(s) to repeat for every depth in the resulting structure, typically either
         *  a set of spaces or tabs
         */
        internal var indent: Indent = FixedStepIndent(INDENT_PATTERN),
    ) {
        fun build() = PrettyFormatter(
            prefixes = prefixes,
            indent = indent,
        )
    }

    companion object {
        val NoPrefixes = Prefixes(emptyMap())
    }
}
