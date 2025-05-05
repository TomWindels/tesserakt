package dev.tesserakt.rdf.trig.serialization

import dev.tesserakt.rdf.serialization.common.Prefixes
import dev.tesserakt.rdf.trig.serialization.PrettyFormatter.*


class TRiGConfig(
    var formatter: Formatter = DEFAULT_FORMATTER
) {

    data class PrettyFormatterConf(
        internal var prefixes: Prefixes = NoPrefixes,
        /**
         * The (group of) character(s) to repeat for every depth in the resulting structure, typically either
         *  a set of spaces or tabs
         */
        internal var indent: Indent = FixedStepIndent(INDENT_PATTERN),
        /**
         * The strategy used to flatten block structures
         */
        internal var flattenStrategy: FlattenStrategy = LengthBasedFlattening(64),
    ) {
        fun build() = PrettyFormatter(
            prefixes = prefixes,
            indent = indent,
            flattenStrategy = flattenStrategy
        )
    }

    companion object {
        private const val INDENT_PATTERN = "    "

        val NoPrefixes = Prefixes(emptyMap())
    }
}
