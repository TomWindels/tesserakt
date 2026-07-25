package dev.tesserakt.rdf.serialization.trig

import dev.tesserakt.rdf.serialization.common.Prefixes


class TriGConfig(
    var base: String = DEFAULT_BASE,
    var formatter: TriGFormatter = DEFAULT_TRIG_FORMATTER
) {

    data class PrettyFormatterConf(
        internal var prefixes: Prefixes = NoPrefixes,
        /**
         * The (group of) character(s) to repeat for every depth in the resulting structure, typically either
         *  a set of spaces or tabs
         */
        internal var indent: PrettyTriGFormatter.Indent = PrettyTriGFormatter.FixedStepIndent(INDENT_PATTERN),
        /**
         * The strategy used to flatten block structures
         */
        internal var flattenStrategy: PrettyTriGFormatter.FlattenStrategy = PrettyTriGFormatter.LengthBasedFlattening(64),
    ) {
        fun build() = PrettyTriGFormatter(
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
